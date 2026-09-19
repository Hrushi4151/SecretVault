package com.secretvault.access.jit.service;

import com.secretvault.access.jit.dto.ApproveJitRequest;
import com.secretvault.access.jit.dto.JitAccessRequestResponse;
import com.secretvault.access.jit.dto.RejectJitRequest;
import com.secretvault.access.jit.dto.SubmitJitRequest;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.dto.PageResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise service managing Just-In-Time (JIT) Temporary Privilege Elevation.
 * Implements strict time-to-live enforcement, dual-custody approval,
 * anti-self-approval protection, and scoped authority containment.
 */
@Service
public class JitAccessService {

    private static final Logger log = LoggerFactory.getLogger(JitAccessService.class);

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "status", "expiresAt", "requestedPermission");
    private static final int MAX_PAGE_SIZE = 100;

    private final JitAccessRequestRepository jitRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretRepository secretRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final EffectiveAccessService effectiveAccessService;
    private final Clock clock;

    public JitAccessService(
            JitAccessRequestRepository jitRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            UserRepository userRepository,
            AuditService auditService,
            EffectiveAccessService effectiveAccessService,
            Clock clock
    ) {
        this.jitRepository = jitRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.secretRepository = secretRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.effectiveAccessService = effectiveAccessService;
        this.clock = clock;
    }

    @Transactional
    public JitAccessRequestResponse submitRequest(UUID workspaceId, SubmitJitRequest request, UUID actorUserId) {
        // 1. Validate Membership
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        // 2. Resolve & Validate Hierarchy
        Environment env = environmentRepository.findById(request.environmentId())
                .orElseThrow(() -> ApiException.badRequest("Environment not found"));

        UUID projectId = request.projectId() != null ? request.projectId() : env.getProjectId();
        if (!env.getProjectId().equals(projectId)) {
            throw ApiException.badRequest("Environment does not belong to the specified project");
        }

        projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.badRequest("Project not found in this workspace"));

        if (request.secretId() != null) {
            Secret secret = secretRepository.findByIdAndEnvironmentId(request.secretId(), request.environmentId())
                    .orElseThrow(() -> ApiException.badRequest("Secret not found in the specified environment"));
            if (secret.getStatus() == SecretStatus.DELETED) {
                throw ApiException.badRequest("Cannot request JIT access on a deleted secret");
            }
        }

        // 3. Permission & Enclave Validation
        if (request.requestedPermission() == AccessPermission.SECRET_BRANCH) {
            if (env.getEnvType() != EnvType.DEVELOPMENT) {
                throw ApiException.badRequest(
                        "Feature branch permission (secret.branch) is strictly prohibited on " + env.getEnvType() + " environments. Feature branches are only permitted in DEVELOPMENT."
                );
            }
        }

        // 4. Duplicate Pending Request Check
        boolean hasPending = jitRepository.existsPendingRequest(
                workspaceId, actorUserId, request.environmentId(), request.secretId(), request.requestedPermission()
        );
        if (hasPending) {
            throw ApiException.conflict("A pending JIT request already exists for this scope and permission");
        }

        // 5. Create JIT Request
        JitAccessRequest jitRequest = new JitAccessRequest(
                workspaceId,
                actorUserId,
                projectId,
                request.environmentId(),
                request.secretId(),
                request.requestedPermission(),
                request.durationMinutes(),
                request.reason().trim()
        );

        JitAccessRequest saved = jitRepository.save(jitRequest);

        auditService.logSuccess(
                AuditAction.JIT_ACCESS_REQUESTED,
                "JIT_REQUEST",
                saved.getId(),
                actorUserId,
                workspaceId,
                "Submitted JIT temporary access request for [" + request.requestedPermission().getCode() + "] for " + request.durationMinutes() + " minutes. Reason: " + request.reason()
        );

        log.info("User [{}] requested JIT access [{}] on env [{}] for permission [{}] in workspace [{}]",
                actorUserId, saved.getId(), request.environmentId(), request.requestedPermission(), workspaceId);

        return mapToResponse(saved);
    }

    @Transactional
    public JitAccessRequestResponse approveRequest(UUID workspaceId, UUID requestId, ApproveJitRequest body, UUID approverUserId) {
        JitAccessRequest request = jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("JIT access request not found in this workspace"));

        if (request.getStatus() != JitStatus.PENDING) {
            throw ApiException.badRequest("JIT request is not in PENDING state (current: " + request.getStatus() + ")");
        }

        // 1. Separation of Duties: Anti-Self-Approval Enforcement
        if (request.getUserId().equals(approverUserId)) {
            throw ApiException.forbidden("Anti-Self-Approval Violation: You cannot approve your own JIT access request");
        }

        // 2. Scoped Approver Authority Check
        effectiveAccessService.checkPermission(
                workspaceId,
                request.getProjectId(),
                request.getEnvironmentId(),
                request.getSecretId(),
                AccessPermission.JIT_APPROVE,
                approverUserId
        );

        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(request.getDurationMinutes()));

        request.setStatus(JitStatus.APPROVED);
        request.setApproverId(approverUserId);
        request.setApprovedAt(now);
        request.setExpiresAt(expiresAt);
        request.setReviewerNotes(body != null && body.reviewerNotes() != null ? body.reviewerNotes().trim() : null);

        JitAccessRequest saved = jitRepository.save(request);

        auditService.logSuccess(
                AuditAction.JIT_ACCESS_APPROVED,
                "JIT_REQUEST",
                saved.getId(),
                approverUserId,
                workspaceId,
                "Approved JIT access for user [" + request.getUserId() + "] on permission [" + request.getRequestedPermission().getCode() + "] active until [" + expiresAt + "]"
        );

        log.info("JIT access request [{}] approved by [{}] for user [{}], expiring at [{}]",
                requestId, approverUserId, request.getUserId(), expiresAt);

        return mapToResponse(saved);
    }

    @Transactional
    public JitAccessRequestResponse rejectRequest(UUID workspaceId, UUID requestId, RejectJitRequest body, UUID reviewerUserId) {
        JitAccessRequest request = jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("JIT access request not found in this workspace"));

        if (request.getStatus() != JitStatus.PENDING) {
            throw ApiException.badRequest("JIT request is not in PENDING state (current: " + request.getStatus() + ")");
        }

        effectiveAccessService.checkPermission(
                workspaceId,
                request.getProjectId(),
                request.getEnvironmentId(),
                request.getSecretId(),
                AccessPermission.JIT_APPROVE,
                reviewerUserId
        );

        String notes = body != null && body.reviewerNotes() != null ? body.reviewerNotes().trim() : "Rejected by security policy";
        request.setStatus(JitStatus.REJECTED);
        request.setApproverId(reviewerUserId);
        request.setReviewerNotes(notes);

        JitAccessRequest saved = jitRepository.save(request);

        auditService.logSuccess(
                AuditAction.JIT_ACCESS_REJECTED,
                "JIT_REQUEST",
                saved.getId(),
                reviewerUserId,
                workspaceId,
                "Rejected JIT access for user [" + request.getUserId() + "]. Rationale: " + notes
        );

        log.info("JIT access request [{}] rejected by [{}] for user [{}]",
                requestId, reviewerUserId, request.getUserId());

        return mapToResponse(saved);
    }

    @Transactional
    public void revokeGrant(UUID workspaceId, UUID requestId, UUID actorUserId) {
        JitAccessRequest request = jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("JIT access request not found in this workspace"));

        if (request.getStatus() != JitStatus.APPROVED) {
            throw ApiException.badRequest("Only APPROVED active JIT grants can be revoked");
        }

        effectiveAccessService.checkPermission(
                workspaceId,
                request.getProjectId(),
                request.getEnvironmentId(),
                request.getSecretId(),
                AccessPermission.JIT_APPROVE,
                actorUserId
        );

        request.setStatus(JitStatus.REVOKED);
        request.setRevokedAt(clock.instant());

        jitRepository.save(request);

        auditService.logSuccess(
                AuditAction.JIT_ACCESS_REVOKED,
                "JIT_REQUEST",
                request.getId(),
                actorUserId,
                workspaceId,
                "Revoked active JIT elevation for user [" + request.getUserId() + "] on permission [" + request.getRequestedPermission().getCode() + "]"
        );

        log.info("JIT access grant [{}] revoked by [{}]", requestId, actorUserId);
    }

    @Transactional
    public void cancelRequest(UUID workspaceId, UUID requestId, UUID actorUserId) {
        JitAccessRequest request = jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("JIT access request not found in this workspace"));

        if (!request.getUserId().equals(actorUserId)) {
            throw ApiException.forbidden("Only the requester can cancel their pending JIT request");
        }

        if (request.getStatus() != JitStatus.PENDING) {
            throw ApiException.badRequest("Only PENDING JIT requests can be cancelled");
        }

        request.setStatus(JitStatus.CANCELLED);
        jitRepository.save(request);

        auditService.logSuccess(
                AuditAction.JIT_ACCESS_CANCELLED,
                "JIT_REQUEST",
                request.getId(),
                actorUserId,
                workspaceId,
                "Cancelled JIT request by requester"
        );

        log.info("JIT access request [{}] cancelled by requester [{}]", requestId, actorUserId);
    }

    @Transactional(readOnly = true)
    public JitAccessRequestResponse getRequestById(UUID workspaceId, UUID requestId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        JitAccessRequest request = jitRepository.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("JIT access request not found in this workspace"));

        return mapToResponse(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<JitAccessRequestResponse> listRequestsPaginated(
            UUID workspaceId,
            JitStatus status,
            UUID userId,
            AccessPermission permission,
            UUID projectId,
            UUID environmentId,
            Pageable pageable,
            UUID actorUserId
    ) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        Pageable safePageable = sanitizePageable(pageable);

        Page<JitAccessRequest> page = jitRepository.findFilteredRequests(
                workspaceId, status, userId, permission, projectId, environmentId, safePageable
        );

        List<JitAccessRequestResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<JitAccessRequestResponse> listRequests(UUID workspaceId, JitStatus statusFilter, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        List<JitAccessRequest> requests = statusFilter != null
                ? jitRepository.findByWorkspaceIdAndStatus(workspaceId, statusFilter)
                : jitRepository.findByWorkspaceId(workspaceId);

        return requests.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JitAccessRequestResponse> getActiveGrants(UUID workspaceId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        List<JitAccessRequest> active = jitRepository.findActiveGrantsForUser(workspaceId, actorUserId, clock.instant());
        return active.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        if (pageSize <= 0) pageSize = 20;

        int pageNumber = Math.max(pageable.getPageNumber(), 0);

        Sort safeSort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (pageable.getSort().isSorted()) {
            List<Sort.Order> safeOrders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    safeOrders.add(order);
                }
            }
            if (!safeOrders.isEmpty()) {
                safeSort = Sort.by(safeOrders);
            }
        }

        return PageRequest.of(pageNumber, pageSize, safeSort);
    }

    private JitAccessRequestResponse mapToResponse(JitAccessRequest req) {
        User user = userRepository.findById(req.getUserId()).orElse(null);
        String userEmail = user != null ? user.getEmail() : "unknown";
        String userFullName = user != null ? user.getFullName() : "Unknown User";

        String approverEmail = req.getApproverId() != null
                ? userRepository.findById(req.getApproverId()).map(User::getEmail).orElse(null)
                : null;

        String projectName = req.getProjectId() != null
                ? projectRepository.findById(req.getProjectId()).map(Project::getName).orElse(null)
                : null;

        Environment env = environmentRepository.findById(req.getEnvironmentId()).orElse(null);
        String envName = env != null ? env.getName() : null;
        boolean isProtected = env != null && (env.isProtected() || env.getEnvType() == com.secretvault.environment.entity.EnvType.PRODUCTION);

        String secretKey = req.getSecretId() != null
                ? secretRepository.findById(req.getSecretId()).map(Secret::getName).orElse(null)
                : null;

        return JitAccessRequestResponse.fromEntity(
                req, userEmail, userFullName, projectName, envName, isProtected, secretKey, approverEmail
        );
    }
}
