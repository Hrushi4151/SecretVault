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
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JitAccessService {

    private static final Logger log = LoggerFactory.getLogger(JitAccessService.class);

    private final JitAccessRequestRepository jitRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretRepository secretRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final EffectiveAccessService effectiveAccessService;

    public JitAccessService(
            JitAccessRequestRepository jitRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            UserRepository userRepository,
            AuditService auditService,
            EffectiveAccessService effectiveAccessService
    ) {
        this.jitRepository = jitRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.secretRepository = secretRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @Transactional
    public JitAccessRequestResponse submitRequest(UUID workspaceId, SubmitJitRequest request, UUID actorUserId) {
        // 1. Validate Membership & JIT Request Authority
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

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.badRequest("Project not found in this workspace"));

        if (request.secretId() != null) {
            Secret secret = secretRepository.findByIdAndEnvironmentId(request.secretId(), request.environmentId())
                    .orElseThrow(() -> ApiException.badRequest("Secret not found in the specified environment"));
        }

        // 3. Create JIT Request
        JitAccessRequest jitRequest = new JitAccessRequest(
                workspaceId,
                actorUserId,
                projectId,
                request.environmentId(),
                request.secretId(),
                request.requestedPermission(),
                request.durationMinutes(),
                request.reason()
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

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(request.getDurationMinutes()));

        request.setStatus(JitStatus.APPROVED);
        request.setApproverId(approverUserId);
        request.setApprovedAt(now);
        request.setExpiresAt(expiresAt);
        request.setReviewerNotes(body != null ? body.reviewerNotes() : null);

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

        request.setStatus(JitStatus.REJECTED);
        request.setApproverId(reviewerUserId);
        request.setReviewerNotes(body.reviewerNotes());

        JitAccessRequest saved = jitRepository.save(request);

        auditService.logSuccess(
                AuditAction.JIT_ACCESS_REJECTED,
                "JIT_REQUEST",
                saved.getId(),
                reviewerUserId,
                workspaceId,
                "Rejected JIT access for user [" + request.getUserId() + "]. Rationale: " + body.reviewerNotes()
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
        request.setRevokedAt(Instant.now());

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

        List<JitAccessRequest> active = jitRepository.findActiveGrantsForUser(workspaceId, actorUserId, Instant.now());
        return active.stream().map(this::mapToResponse).collect(Collectors.toList());
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
