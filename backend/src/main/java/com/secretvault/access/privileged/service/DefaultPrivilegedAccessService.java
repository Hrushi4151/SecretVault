package com.secretvault.access.privileged.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.privileged.dto.*;
import com.secretvault.access.privileged.entity.PrivilegedAccessApproval;
import com.secretvault.access.privileged.entity.PrivilegedAccessElevation;
import com.secretvault.access.privileged.entity.PrivilegedAccessPolicy;
import com.secretvault.access.privileged.entity.PrivilegedAccessRequest;
import com.secretvault.access.privileged.model.*;
import com.secretvault.access.privileged.repository.PrivilegedAccessApprovalRepository;
import com.secretvault.access.privileged.repository.PrivilegedAccessElevationRepository;
import com.secretvault.access.privileged.repository.PrivilegedAccessPolicyRepository;
import com.secretvault.access.privileged.repository.PrivilegedAccessRequestRepository;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.auth.stepup.model.StepUpFactor;
import com.secretvault.auth.stepup.service.StepUpAuthenticationService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DefaultPrivilegedAccessService implements PrivilegedAccessService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPrivilegedAccessService.class);

    private final PrivilegedAccessPolicyRepository policyRepository;
    private final PrivilegedAccessRequestRepository requestRepository;
    private final PrivilegedAccessApprovalRepository approvalRepository;
    private final PrivilegedAccessElevationRepository elevationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretRepository secretRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final StepUpAuthenticationService stepUpService;
    private final EffectiveAccessService effectiveAccessService;
    private final Clock clock;

    public DefaultPrivilegedAccessService(
            PrivilegedAccessPolicyRepository policyRepository,
            PrivilegedAccessRequestRepository requestRepository,
            PrivilegedAccessApprovalRepository approvalRepository,
            PrivilegedAccessElevationRepository elevationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            UserRepository userRepository,
            AuditService auditService,
            @Autowired(required = false) StepUpAuthenticationService stepUpService,
            @Lazy @Autowired(required = false) EffectiveAccessService effectiveAccessService,
            @Autowired(required = false) Clock clock
    ) {
        this.policyRepository = policyRepository;
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.elevationRepository = elevationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.secretRepository = secretRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.stepUpService = stepUpService;
        this.effectiveAccessService = effectiveAccessService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    @Transactional
    public PrivilegedAccessRequestResponse createRequest(
            UUID workspaceId,
            UUID callerId,
            String sessionIdentifier,
            CreatePrivilegedAccessRequest request
    ) {
        validateWorkspaceMembership(workspaceId, callerId);
        validateResourceHierarchy(workspaceId, request.scopeType(), request.projectId(), request.environmentId(), request.secretId());

        PrivilegedAction action = request.action();
        PrivilegedPolicyScope scopeType = request.scopeType() != null ? request.scopeType() : PrivilegedPolicyScope.WORKSPACE;
        PrivilegedAccessPolicy policy = getEffectivePolicy(workspaceId, scopeType, request.projectId(), request.environmentId(), request.secretId(), action);

        if (!policy.isEnabled()) {
            throw ApiException.forbidden("Privileged action [" + action + "] is disabled by policy for this scope");
        }

        if (policy.isRequireJustification() && (request.justification() == null || request.justification().trim().length() < 10)) {
            throw ApiException.badRequest("Detailed justification (at least 10 characters) is mandatory for this privileged operation");
        }

        int duration = request.durationMinutes() != null ? request.durationMinutes() : 60;
        if (duration > policy.getMaxDurationMinutes()) {
            throw ApiException.badRequest("Requested duration (" + duration + "m) exceeds maximum policy limit of " + policy.getMaxDurationMinutes() + " minutes");
        }

        UUID targetUserId = request.targetUserId() != null ? request.targetUserId() : callerId;
        if (!targetUserId.equals(callerId)) {
            validateWorkspaceMembership(workspaceId, targetUserId);
        }

        // Check Step-Up Authentication if required
        if (policy.isRequireStepUp() && request.stepUpProof() != null && !request.stepUpProof().isBlank()) {
            consumeStepUpProofSafe(request.stepUpProof(), callerId, sessionIdentifier, mapToStepUpAction(action), workspaceId, request.projectId(), request.environmentId());
        }

        int quorum = policy.isRequireApproval() ? policy.getApprovalQuorum() : 0;

        PrivilegedAccessRequest entity = new PrivilegedAccessRequest(
                workspaceId,
                callerId,
                targetUserId,
                action,
                scopeType,
                request.projectId(),
                request.environmentId(),
                request.secretId(),
                request.requestedPermissions(),
                request.justification().trim(),
                duration,
                false,
                quorum
        );

        Instant now = clock.instant();
        // Pending requests expire in 24 hours if not approved
        entity.setExpiresAt(now.plus(Duration.ofHours(24)));

        if (!policy.isRequireApproval()) {
            // Auto-approved
            entity.setStatus(PrivilegedRequestStatus.APPROVED);
            entity.setApprovedAt(now);
            entity.setExpiresAt(now.plus(Duration.ofMinutes(duration)));
            createElevationForRequest(entity, now);
        }

        PrivilegedAccessRequest saved = requestRepository.save(entity);

        auditService.recordAudit(
                null,
                workspaceId,
                callerId,
                "USER",
                AuditAction.PRIVILEGED_ACCESS_REQUESTED,
                "PRIVILEGED_REQUEST",
                saved.getId(),
                "ACTION: " + action + ", SCOPE: " + scopeType + ", DURATION: " + duration + "m",
                null,
                "SUCCESS"
        );

        return buildRequestResponse(saved, callerId);
    }

    @Override
    @Transactional
    public PrivilegedAccessRequestResponse approveRequest(
            UUID workspaceId,
            UUID requestId,
            UUID approverId,
            String sessionIdentifier,
            ApprovePrivilegedRequest request
    ) {
        WorkspaceMembership approverMembership = validateWorkspaceMembership(workspaceId, approverId);

        PrivilegedAccessRequest req = requestRepository.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Privileged access request not found"));

        Instant now = clock.instant();

        // 1. Expiration check
        if (req.getStatus() == PrivilegedRequestStatus.PENDING && req.getExpiresAt() != null && !now.isBefore(req.getExpiresAt())) {
            req.setStatus(PrivilegedRequestStatus.EXPIRED);
            requestRepository.save(req);
            auditService.recordAudit(null, workspaceId, approverId, "USER", AuditAction.PRIVILEGED_ACCESS_EXPIRED, "PRIVILEGED_REQUEST", req.getId(), "Request expired before approval", null, "EXPIRED");
            throw ApiException.badRequest("Privileged access request has expired");
        }

        if (req.getStatus() != PrivilegedRequestStatus.PENDING) {
            throw ApiException.badRequest("Cannot approve request in status: " + req.getStatus());
        }

        PrivilegedAccessPolicy policy = getEffectivePolicy(workspaceId, req.getScopeType(), req.getProjectId(), req.getEnvironmentId(), req.getSecretId(), req.getAction());

        // 2. Anti-Self-Approval Enforcement
        if (policy.isPreventSelfApproval()) {
            if (approverId.equals(req.getRequesterId()) || approverId.equals(req.getTargetUserId())) {
                throw ApiException.forbidden("Anti-self-approval rule: requesters and target users cannot approve their own privileged access requests");
            }
        }

        // 3. Approval Authority Validation
        validateApprovalAuthority(approverMembership, req);

        // 4. Duplicate Approval / Idempotency Check
        Optional<PrivilegedAccessApproval> existingApproval = approvalRepository.findByRequestIdAndApproverId(requestId, approverId);
        if (existingApproval.isPresent()) {
            throw ApiException.badRequest("Duplicate approval: you have already submitted a decision for this request");
        }

        // 5. Step-Up Authentication for Approver if required
        StepUpFactor usedFactor = null;
        if (policy.isRequireStepUp() && request.stepUpProof() != null && !request.stepUpProof().isBlank()) {
            consumeStepUpProofSafe(request.stepUpProof(), approverId, sessionIdentifier, StepUpAction.PRIVILEGED_APPROVE, workspaceId, req.getProjectId(), req.getEnvironmentId());
            usedFactor = StepUpFactor.PASSWORD; // default factor representation
        }

        // 6. Record Approval
        PrivilegedAccessApproval approval = new PrivilegedAccessApproval(
                requestId,
                approverId,
                request.decision() != null ? request.decision() : ApprovalDecision.APPROVED,
                request.notes(),
                usedFactor,
                request.stepUpProof()
        );
        approvalRepository.save(approval);

        // 7. Atomic Quorum Check evaluated against current effective policy & request requirement
        int requiredQuorum = Math.max(req.getRequiredQuorum(), policy.isRequireApproval() ? policy.getApprovalQuorum() : 1);
        req.setRequiredQuorum(requiredQuorum);
        long distinctApproved = approvalRepository.countDistinctApproversByDecision(requestId, ApprovalDecision.APPROVED);
        req.setCurrentApprovalsCount((int) distinctApproved);

        auditService.recordAudit(
                null,
                workspaceId,
                approverId,
                "USER",
                AuditAction.PRIVILEGED_ACCESS_APPROVED,
                "PRIVILEGED_REQUEST",
                req.getId(),
                "APPROVED by " + approverId + ". Current quorum: " + distinctApproved + "/" + requiredQuorum,
                null,
                "SUCCESS"
        );

        if (distinctApproved >= requiredQuorum) {
            req.setStatus(PrivilegedRequestStatus.APPROVED);
            req.setApprovedAt(now);
            req.setExpiresAt(now.plus(Duration.ofMinutes(req.getDurationMinutes())));

            // Automatically create active elevation
            createElevationForRequest(req, now);

            auditService.recordAudit(
                    null,
                    workspaceId,
                    approverId,
                    "USER",
                    AuditAction.APPROVAL_QUORUM_REACHED,
                    "PRIVILEGED_REQUEST",
                    req.getId(),
                    "Quorum satisfied (" + distinctApproved + "/" + req.getRequiredQuorum() + "). Temporary elevation activated until " + req.getExpiresAt(),
                    null,
                    "SUCCESS"
            );
        }

        PrivilegedAccessRequest saved = requestRepository.save(req);
        return buildRequestResponse(saved, approverId);
    }

    @Override
    @Transactional
    public PrivilegedAccessRequestResponse rejectRequest(
            UUID workspaceId,
            UUID requestId,
            UUID approverId,
            RejectPrivilegedRequest request
    ) {
        WorkspaceMembership approverMembership = validateWorkspaceMembership(workspaceId, approverId);

        PrivilegedAccessRequest req = requestRepository.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Privileged access request not found"));

        if (req.getStatus() != PrivilegedRequestStatus.PENDING) {
            throw ApiException.badRequest("Cannot reject request in status: " + req.getStatus());
        }

        validateApprovalAuthority(approverMembership, req);

        PrivilegedAccessApproval approval = new PrivilegedAccessApproval(
                requestId,
                approverId,
                ApprovalDecision.REJECTED,
                request.reason(),
                null,
                null
        );
        approvalRepository.save(approval);

        req.setStatus(PrivilegedRequestStatus.REJECTED);
        req.setRejectionReason(request.reason());
        PrivilegedAccessRequest saved = requestRepository.save(req);

        auditService.recordAudit(
                null,
                workspaceId,
                approverId,
                "USER",
                AuditAction.PRIVILEGED_ACCESS_REJECTED,
                "PRIVILEGED_REQUEST",
                req.getId(),
                "REJECTED by " + approverId + ". Reason: " + request.reason(),
                null,
                "REJECTED"
        );

        return buildRequestResponse(saved, approverId);
    }

    @Override
    @Transactional
    public PrivilegedAccessRequestResponse cancelRequest(
            UUID workspaceId,
            UUID requestId,
            UUID callerId,
            CancelPrivilegedRequest request
    ) {
        WorkspaceMembership membership = validateWorkspaceMembership(workspaceId, callerId);

        PrivilegedAccessRequest req = requestRepository.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Privileged access request not found"));

        if (req.getStatus() != PrivilegedRequestStatus.PENDING) {
            throw ApiException.badRequest("Cannot cancel request in status: " + req.getStatus());
        }

        boolean isRequester = callerId.equals(req.getRequesterId());
        boolean isWorkspaceAdmin = membership.getRole() == WorkspaceRole.OWNER || membership.getRole() == WorkspaceRole.ADMIN;

        if (!isRequester && !isWorkspaceAdmin) {
            throw ApiException.forbidden("Only the original requester or workspace administrators can cancel this request");
        }

        req.setStatus(PrivilegedRequestStatus.CANCELLED);
        req.setCancellationReason(request != null ? request.reason() : "Cancelled by user");
        PrivilegedAccessRequest saved = requestRepository.save(req);

        auditService.recordAudit(
                null,
                workspaceId,
                callerId,
                "USER",
                AuditAction.PRIVILEGED_ACCESS_CANCELLED,
                "PRIVILEGED_REQUEST",
                req.getId(),
                "CANCELLED by " + callerId,
                null,
                "CANCELLED"
        );

        return buildRequestResponse(saved, callerId);
    }

    @Override
    @Transactional
    public PrivilegedAccessRequestResponse executeRequest(
            UUID workspaceId,
            UUID requestId,
            UUID callerId,
            String sessionIdentifier,
            String stepUpProof
    ) {
        validateWorkspaceMembership(workspaceId, callerId);

        PrivilegedAccessRequest req = requestRepository.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Privileged access request not found"));

        Instant now = clock.instant();

        if (req.getStatus() == PrivilegedRequestStatus.APPROVED && req.getExpiresAt() != null && !now.isBefore(req.getExpiresAt())) {
            req.setStatus(PrivilegedRequestStatus.EXPIRED);
            requestRepository.save(req);
            throw ApiException.badRequest("Privileged access approval has expired");
        }

        if (req.getStatus() != PrivilegedRequestStatus.APPROVED) {
            throw ApiException.badRequest("Cannot execute request in status: " + req.getStatus() + ". Only APPROVED requests can be executed.");
        }

        if (!callerId.equals(req.getRequesterId()) && !callerId.equals(req.getTargetUserId())) {
            WorkspaceMembership mem = validateWorkspaceMembership(workspaceId, callerId);
            if (mem.getRole() != WorkspaceRole.OWNER && mem.getRole() != WorkspaceRole.ADMIN) {
                throw ApiException.forbidden("Only the authorized target user or workspace administrator can execute this approved request");
            }
        }

        if (stepUpProof != null && !stepUpProof.isBlank()) {
            consumeStepUpProofSafe(stepUpProof, callerId, sessionIdentifier, StepUpAction.PRIVILEGED_EXECUTE, workspaceId, req.getProjectId(), req.getEnvironmentId());
        }

        req.setStatus(PrivilegedRequestStatus.EXECUTED);
        req.setExecutedAt(now);
        PrivilegedAccessRequest saved = requestRepository.save(req);

        // Ensure active elevation exists
        Optional<PrivilegedAccessElevation> elevOpt = elevationRepository.findByRequestId(requestId);
        if (elevOpt.isEmpty()) {
            createElevationForRequest(saved, now);
        }

        auditService.recordAudit(
                null,
                workspaceId,
                callerId,
                "USER",
                AuditAction.PRIVILEGED_ACCESS_EXECUTED,
                "PRIVILEGED_REQUEST",
                req.getId(),
                "EXECUTED by " + callerId + ". Action: " + req.getAction(),
                null,
                "SUCCESS"
        );

        return buildRequestResponse(saved, callerId);
    }

    @Override
    @Transactional
    public PrivilegedAccessRequestResponse revokeRequest(
            UUID workspaceId,
            UUID requestId,
            UUID callerId,
            RevokePrivilegedRequest request
    ) {
        WorkspaceMembership membership = validateWorkspaceMembership(workspaceId, callerId);

        PrivilegedAccessRequest req = requestRepository.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Privileged access request not found"));

        boolean isAuthorized = callerId.equals(req.getRequesterId()) ||
                membership.getRole() == WorkspaceRole.OWNER ||
                membership.getRole() == WorkspaceRole.ADMIN;

        if (!isAuthorized) {
            throw ApiException.forbidden("Insufficient authority to revoke this privileged access request");
        }

        Instant now = clock.instant();
        req.setStatus(PrivilegedRequestStatus.REVOKED);
        req.setRevokedAt(now);
        req.setRevokedBy(callerId);
        req.setRevocationReason(request.reason());
        PrivilegedAccessRequest saved = requestRepository.save(req);

        // Revoke active elevations
        Optional<PrivilegedAccessElevation> elevOpt = elevationRepository.findByRequestId(requestId);
        if (elevOpt.isPresent()) {
            PrivilegedAccessElevation elev = elevOpt.get();
            elev.setStatus(ElevationStatus.REVOKED);
            elev.setRevokedAt(now);
            elev.setRevokedBy(callerId);
            elev.setRevocationReason(request.reason());
            elevationRepository.save(elev);
        }

        auditService.recordAudit(
                null,
                workspaceId,
                callerId,
                "USER",
                AuditAction.PRIVILEGED_ACCESS_REVOKED,
                "PRIVILEGED_REQUEST",
                req.getId(),
                "REVOKED by " + callerId + ". Reason: " + request.reason(),
                null,
                "REVOKED"
        );

        return buildRequestResponse(saved, callerId);
    }

    @Override
    @Transactional
    public PrivilegedAccessRequestResponse breakGlass(
            UUID workspaceId,
            UUID callerId,
            String sessionIdentifier,
            BreakGlassRequest request
    ) {
        validateWorkspaceMembership(workspaceId, callerId);

        if (request.justification() == null || request.justification().trim().length() < 20) {
            throw ApiException.badRequest("Mandatory justification for Break-Glass emergency access must be comprehensive (at least 20 characters)");
        }

        if (request.stepUpProof() == null || request.stepUpProof().isBlank()) {
            throw ApiException.forbidden("STEP_UP_REQUIRED", "Strong Step-Up Authentication proof is mandatory to execute emergency Break-Glass access");
        }

        validateResourceHierarchy(workspaceId, PrivilegedPolicyScope.ENVIRONMENT, request.projectId(), request.environmentId(), request.secretId());

        PrivilegedAction action = request.action() != null ? request.action() : PrivilegedAction.BREAK_GLASS_REQUEST;
        PrivilegedAccessPolicy policy = getEffectivePolicy(workspaceId, PrivilegedPolicyScope.ENVIRONMENT, request.projectId(), request.environmentId(), request.secretId(), action);

        if (!policy.isBreakGlassAllowed()) {
            throw ApiException.forbidden("Break-Glass emergency access is prohibited by security policy on this scope");
        }

        int duration = request.durationMinutes() != null ? request.durationMinutes() : 30;
        if (duration > policy.getEmergencyDurationLimitMinutes()) {
            throw ApiException.badRequest("Requested emergency duration (" + duration + "m) exceeds policy limit of " + policy.getEmergencyDurationLimitMinutes() + " minutes");
        }

        // Strong Step-Up verification
        consumeStepUpProofSafe(request.stepUpProof(), callerId, sessionIdentifier, StepUpAction.BREAK_GLASS_REQUEST, workspaceId, request.projectId(), request.environmentId());

        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(duration));

        PrivilegedAccessRequest req = new PrivilegedAccessRequest(
                workspaceId,
                callerId,
                callerId,
                action,
                PrivilegedPolicyScope.ENVIRONMENT,
                request.projectId(),
                request.environmentId(),
                request.secretId(),
                request.requestedPermissions(),
                request.justification().trim(),
                duration,
                true,
                1
        );
        req.setStatus(PrivilegedRequestStatus.EXECUTED);
        req.setApprovedAt(now);
        req.setExecutedAt(now);
        req.setExpiresAt(expiresAt);
        PrivilegedAccessRequest savedReq = requestRepository.save(req);

        // Create explicit emergency elevation
        AccessPermission grantedPerm = mapActionToPermission(action, request.requestedPermissions());
        PrivilegedAccessElevation elevation = new PrivilegedAccessElevation(
                workspaceId,
                savedReq.getId(),
                callerId,
                action,
                PrivilegedPolicyScope.ENVIRONMENT,
                request.projectId(),
                request.environmentId(),
                request.secretId(),
                grantedPerm,
                true,
                now,
                expiresAt
        );
        elevationRepository.save(elevation);

        // Security events and audit logging
        auditService.recordAudit(
                null,
                workspaceId,
                callerId,
                "USER",
                AuditAction.BREAK_GLASS_REQUESTED,
                "BREAK_GLASS",
                savedReq.getId(),
                "EMERGENCY BREAK-GLASS REQUESTED. Scope: Project=" + request.projectId() + ", Env=" + request.environmentId() + ", Reason=" + request.justification(),
                null,
                "SUCCESS"
        );

        auditService.recordAudit(
                null,
                workspaceId,
                callerId,
                "USER",
                AuditAction.BREAK_GLASS_EXECUTED,
                "BREAK_GLASS",
                savedReq.getId(),
                "EMERGENCY BREAK-GLASS EXECUTED. Active until: " + expiresAt,
                null,
                "SUCCESS"
        );

        return buildRequestResponse(savedReq, callerId);
    }

    @Override
    @Transactional(readOnly = true)
    public PrivilegedAccessRequestResponse getRequest(UUID workspaceId, UUID requestId, UUID callerId) {
        validateWorkspaceMembership(workspaceId, callerId);
        PrivilegedAccessRequest req = requestRepository.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Privileged access request not found"));
        return buildRequestResponse(req, callerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrivilegedAccessRequestResponse> listRequests(
            UUID workspaceId,
            PrivilegedRequestStatus status,
            Boolean myRequestsOnly,
            Boolean awaitingMyApprovalOnly,
            UUID callerId
    ) {
        WorkspaceMembership membership = validateWorkspaceMembership(workspaceId, callerId);

        List<PrivilegedAccessRequest> requests;
        if (Boolean.TRUE.equals(myRequestsOnly)) {
            requests = requestRepository.findByWorkspaceIdAndRequesterIdOrderByCreatedAtDesc(workspaceId, callerId);
        } else if (status != null) {
            requests = requestRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status);
        } else {
            requests = requestRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        }

        if (Boolean.TRUE.equals(awaitingMyApprovalOnly)) {
            requests = requests.stream()
                    .filter(r -> r.getStatus() == PrivilegedRequestStatus.PENDING)
                    .filter(r -> !callerId.equals(r.getRequesterId()) && !callerId.equals(r.getTargetUserId()))
                    .filter(r -> isCallerEligibleApprover(membership, r))
                    .collect(Collectors.toList());
        }

        return requests.stream()
                .map(r -> buildRequestResponse(r, callerId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrivilegedAccessElevationResponse> listElevations(UUID workspaceId, Boolean activeOnly, UUID callerId) {
        validateWorkspaceMembership(workspaceId, callerId);
        Instant now = clock.instant();

        List<PrivilegedAccessElevation> elevations;
        if (Boolean.TRUE.equals(activeOnly)) {
            elevations = elevationRepository.findActiveElevationsForUser(workspaceId, callerId, now);
        } else {
            elevations = elevationRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        }

        return elevations.stream()
                .map(e -> buildElevationResponse(e, now))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void revokeElevation(UUID workspaceId, UUID elevationId, UUID callerId, String reason) {
        WorkspaceMembership membership = validateWorkspaceMembership(workspaceId, callerId);
        PrivilegedAccessElevation elev = elevationRepository.findByIdAndWorkspaceId(elevationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Elevation grant not found"));

        boolean isAuthorized = callerId.equals(elev.getUserId()) ||
                membership.getRole() == WorkspaceRole.OWNER ||
                membership.getRole() == WorkspaceRole.ADMIN;

        if (!isAuthorized) {
            throw ApiException.forbidden("Insufficient authority to revoke this elevation grant");
        }

        Instant now = clock.instant();
        elev.setStatus(ElevationStatus.REVOKED);
        elev.setRevokedAt(now);
        elev.setRevokedBy(callerId);
        elev.setRevocationReason(reason != null ? reason : "Revoked by user");
        elevationRepository.save(elev);

        if (elev.getRequestId() != null) {
            requestRepository.findById(elev.getRequestId()).ifPresent(req -> {
                req.setStatus(PrivilegedRequestStatus.REVOKED);
                req.setRevokedAt(now);
                req.setRevokedBy(callerId);
                req.setRevocationReason(reason != null ? reason : "Revoked by user");
                requestRepository.save(req);
            });
        }

        AuditAction auditAction = elev.isBreakGlass() ? AuditAction.BREAK_GLASS_REVOKED : AuditAction.PRIVILEGED_ACCESS_REVOKED;
        auditService.recordAudit(null, workspaceId, callerId, "USER", auditAction, "PRIVILEGED_ELEVATION", elev.getId(), "Elevation revoked. Reason: " + reason, null, "REVOKED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrivilegedAccessPolicyResponse> listPolicies(UUID workspaceId, UUID callerId) {
        validateWorkspaceMembership(workspaceId, callerId);
        List<PrivilegedAccessPolicy> policies = policyRepository.findByWorkspaceId(workspaceId);
        return policies.stream().map(this::buildPolicyResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PrivilegedAccessPolicyResponse updatePolicy(
            UUID workspaceId,
            UUID policyId,
            UUID callerId,
            String sessionIdentifier,
            UpdatePrivilegedPolicyRequest request
    ) {
        WorkspaceMembership membership = validateWorkspaceMembership(workspaceId, callerId);
        if (membership.getRole() != WorkspaceRole.OWNER && membership.getRole() != WorkspaceRole.ADMIN) {
            throw ApiException.forbidden("Only Workspace OWNER or ADMIN can modify Privileged Access Policies");
        }

        PrivilegedAccessPolicy policy = policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Privileged Access Policy not found"));

        if (request.stepUpProof() != null && !request.stepUpProof().isBlank()) {
            consumeStepUpProofSafe(request.stepUpProof(), callerId, sessionIdentifier, StepUpAction.PRIVILEGED_POLICY_CHANGE, workspaceId, policy.getProjectId(), policy.getEnvironmentId());
        }

        if (request.enabled() != null) policy.setEnabled(request.enabled());
        if (request.requireStepUp() != null) policy.setRequireStepUp(request.requireStepUp());
        if (request.allowedStepUpFactors() != null) policy.setAllowedStepUpFactors(request.allowedStepUpFactors());
        if (request.requireApproval() != null) policy.setRequireApproval(request.requireApproval());
        if (request.approvalQuorum() != null) policy.setApprovalQuorum(Math.max(1, request.approvalQuorum()));
        if (request.preventSelfApproval() != null) policy.setPreventSelfApproval(request.preventSelfApproval());
        if (request.requireJustification() != null) policy.setRequireJustification(request.requireJustification());
        if (request.maxDurationMinutes() != null) policy.setMaxDurationMinutes(request.maxDurationMinutes());
        if (request.productionProtected() != null) policy.setProductionProtected(request.productionProtected());
        if (request.breakGlassAllowed() != null) policy.setBreakGlassAllowed(request.breakGlassAllowed());
        if (request.breakGlassRequiresReason() != null) policy.setBreakGlassRequiresReason(request.breakGlassRequiresReason());
        if (request.breakGlassRequiresAudit() != null) policy.setBreakGlassRequiresAudit(request.breakGlassRequiresAudit());
        if (request.emergencyDurationLimitMinutes() != null) policy.setEmergencyDurationLimitMinutes(request.emergencyDurationLimitMinutes());

        PrivilegedAccessPolicy saved = policyRepository.save(policy);

        auditService.recordAudit(
                null,
                workspaceId,
                callerId,
                "USER",
                AuditAction.PRIVILEGED_POLICY_CHANGED,
                "PRIVILEGED_POLICY",
                policy.getId(),
                "Privileged policy updated: Quorum=" + policy.getApprovalQuorum() + ", RequireApproval=" + policy.isRequireApproval(),
                null,
                "SUCCESS"
        );

        return buildPolicyResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PrivilegedAccessPolicy getEffectivePolicy(
            UUID workspaceId,
            PrivilegedPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            PrivilegedAction action
    ) {
        List<PrivilegedAccessPolicy> policies = policyRepository.findApplicablePolicies(workspaceId, action);

        // Most specific match first: SECRET -> ENVIRONMENT -> PROJECT -> WORKSPACE
        if (secretId != null) {
            Optional<PrivilegedAccessPolicy> secretPol = policies.stream()
                    .filter(p -> p.getScopeType() == PrivilegedPolicyScope.SECRET && secretId.equals(p.getSecretId()))
                    .findFirst();
            if (secretPol.isPresent()) return secretPol.get();
        }

        if (environmentId != null) {
            Optional<PrivilegedAccessPolicy> envPol = policies.stream()
                    .filter(p -> p.getScopeType() == PrivilegedPolicyScope.ENVIRONMENT && environmentId.equals(p.getEnvironmentId()))
                    .findFirst();
            if (envPol.isPresent()) return envPol.get();
        }

        if (projectId != null) {
            Optional<PrivilegedAccessPolicy> projPol = policies.stream()
                    .filter(p -> p.getScopeType() == PrivilegedPolicyScope.PROJECT && projectId.equals(p.getProjectId()))
                    .findFirst();
            if (projPol.isPresent()) return projPol.get();
        }

        // Workspace scope match
        Optional<PrivilegedAccessPolicy> wsPol = policies.stream()
                .filter(p -> p.getScopeType() == PrivilegedPolicyScope.WORKSPACE && (p.getAction() == action || p.getAction() == null))
                .findFirst();
        if (wsPol.isPresent()) return wsPol.get();

        // Default fallback policy
        PrivilegedAccessPolicy defaultPolicy = new PrivilegedAccessPolicy(
                workspaceId,
                PrivilegedPolicyScope.WORKSPACE,
                null,
                null,
                null,
                action,
                true,
                1,
                true,
                true,
                60
        );
        return defaultPolicy;
    }

    private void createElevationForRequest(PrivilegedAccessRequest req, Instant now) {
        AccessPermission grantedPerm = mapActionToPermission(req.getAction(), req.getRequestedPermissions());
        PrivilegedAccessElevation elevation = new PrivilegedAccessElevation(
                req.getWorkspaceId(),
                req.getId(),
                req.getTargetUserId(),
                req.getAction(),
                req.getScopeType(),
                req.getProjectId(),
                req.getEnvironmentId(),
                req.getSecretId(),
                grantedPerm,
                req.isBreakGlass(),
                now,
                req.getExpiresAt()
        );
        elevationRepository.save(elevation);
    }

    private WorkspaceMembership validateWorkspaceMembership(UUID workspaceId, UUID userId) {
        if (workspaceId == null || userId == null) {
            throw ApiException.unauthorized("Authentication and workspace context required");
        }
        if (!workspaceRepository.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace not found");
        }
        if (userRepository != null) {
            userRepository.findById(userId).ifPresent(u -> {
                if (u.getStatus() != null && u.getStatus() != UserStatus.ACTIVE) {
                    throw ApiException.forbidden("User account is " + u.getStatus());
                }
            });
        }
        return membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not an active member of this workspace"));
    }

    private void validateResourceHierarchy(UUID workspaceId, PrivilegedPolicyScope scope, UUID projectId, UUID environmentId, UUID secretId) {
        if (projectId != null) {
            projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                    .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));
        }
        if (environmentId != null) {
            Environment env = environmentRepository.findById(environmentId)
                    .orElseThrow(() -> ApiException.notFound("Environment not found"));
            if (projectId != null && !env.getProjectId().equals(projectId)) {
                throw ApiException.badRequest("Environment does not belong to the specified project");
            }
        }
        if (secretId != null) {
            Secret secret = secretRepository.findById(secretId)
                    .orElseThrow(() -> ApiException.notFound("Secret not found"));
            if (environmentId != null && !secret.getEnvironmentId().equals(environmentId)) {
                throw ApiException.badRequest("Secret does not belong to the specified environment");
            }
        }
    }

    private void validateApprovalAuthority(WorkspaceMembership approverMembership, PrivilegedAccessRequest req) {
        WorkspaceRole role = approverMembership.getRole();
        if (role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN) {
            return; // Workspace administrators have global governance approval authority
        }

        // For non-global admins, verify scoped authority
        if (req.getProjectId() != null && effectiveAccessService != null) {
            boolean authorizedForProject = effectiveAccessService.isUserAuthorizedForProject(
                    req.getWorkspaceId(),
                    projectRepository.findById(req.getProjectId()).orElse(null),
                    approverMembership,
                    approverMembership.getUserId()
            );
            if (!authorizedForProject) {
                throw ApiException.forbidden("Approver does not have governance authority for this project");
            }
        } else {
            throw ApiException.forbidden("Approval of workspace-level privileged operations requires OWNER or ADMIN role");
        }
    }

    private boolean isCallerEligibleApprover(WorkspaceMembership membership, PrivilegedAccessRequest req) {
        try {
            validateApprovalAuthority(membership, req);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void consumeStepUpProofSafe(
            String stepUpProof,
            UUID userId,
            String sessionIdentifier,
            StepUpAction action,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId
    ) {
        if (stepUpService != null && stepUpProof != null && !stepUpProof.isBlank()) {
            StepUpContext context = new StepUpContext(workspaceId, projectId, environmentId, null, null, Collections.emptyMap());
            stepUpService.verifyAndConsumeProof(stepUpProof, userId, sessionIdentifier, action, context);
        }
    }

    private StepUpAction mapToStepUpAction(PrivilegedAction action) {
        return switch (action) {
            case SECRET_REVEAL -> StepUpAction.SECRET_REVEAL;
            case SECRET_DELETE -> StepUpAction.SECRET_DELETE;
            case SECRET_ROLLBACK -> StepUpAction.SECRET_ROLLBACK;
            case ENVIRONMENT_PROMOTE -> StepUpAction.ENVIRONMENT_PROMOTE;
            case ACCESS_GRANT -> StepUpAction.ACCESS_GRANT;
            case ACCESS_REVOKE -> StepUpAction.ACCESS_REVOKE;
            case JIT_APPROVE -> StepUpAction.JIT_APPROVE;
            case JIT_REVOKE -> StepUpAction.JIT_REVOKE;
            case SESSION_REVOKE_ALL -> StepUpAction.SESSION_REVOKE_ALL;
            case MFA_DISABLE -> StepUpAction.MFA_DISABLE;
            case WEBAUTHN_CREDENTIAL_REVOKE -> StepUpAction.WEBAUTHN_CREDENTIAL_REVOKE;
            case ROLE_CHANGE -> StepUpAction.ROLE_CHANGE;
            case MEMBER_REMOVE -> StepUpAction.MEMBER_REMOVE;
            case PROJECT_ACCESS_CHANGE -> StepUpAction.PROJECT_ACCESS_CHANGE;
            case ENVIRONMENT_ACCESS_CHANGE -> StepUpAction.ENVIRONMENT_ACCESS_CHANGE;
            case PRIVILEGED_POLICY_CHANGE -> StepUpAction.PRIVILEGED_POLICY_CHANGE;
            case BREAK_GLASS_REQUEST -> StepUpAction.BREAK_GLASS_REQUEST;
        };
    }

    private AccessPermission mapActionToPermission(PrivilegedAction action, String requestedPermissions) {
        if (requestedPermissions != null && !requestedPermissions.isBlank()) {
            Optional<AccessPermission> parsed = AccessPermission.tryFromCode(requestedPermissions.trim());
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        return switch (action) {
            case SECRET_REVEAL -> AccessPermission.SECRET_REVEAL;
            case SECRET_DELETE -> AccessPermission.SECRET_DELETE;
            case SECRET_ROLLBACK -> AccessPermission.SECRET_ROLLBACK;
            case ENVIRONMENT_PROMOTE -> AccessPermission.ENVIRONMENT_PROMOTE;
            case ACCESS_GRANT, ACCESS_REVOKE -> AccessPermission.ACCESS_MANAGE;
            case JIT_APPROVE, JIT_REVOKE -> AccessPermission.JIT_APPROVE;
            default -> null;
        };
    }

    private PrivilegedAccessRequestResponse buildRequestResponse(PrivilegedAccessRequest req, UUID callerId) {
        User requester = userRepository.findById(req.getRequesterId()).orElse(null);
        User target = userRepository.findById(req.getTargetUserId()).orElse(null);
        Project project = req.getProjectId() != null ? projectRepository.findById(req.getProjectId()).orElse(null) : null;
        Environment env = req.getEnvironmentId() != null ? environmentRepository.findById(req.getEnvironmentId()).orElse(null) : null;
        Secret secret = req.getSecretId() != null ? secretRepository.findById(req.getSecretId()).orElse(null) : null;

        List<PrivilegedAccessApproval> approvals = approvalRepository.findByRequestId(req.getId());
        List<PrivilegedAccessApprovalResponse> approvalResponses = approvals.stream().map(a -> {
            User approver = userRepository.findById(a.getApproverId()).orElse(null);
            return PrivilegedAccessApprovalResponse.fromEntity(
                    a,
                    approver != null ? approver.getEmail() : null,
                    approver != null ? approver.getFullName() : null
            );
        }).collect(Collectors.toList());

        boolean canApprove = req.getStatus() == PrivilegedRequestStatus.PENDING &&
                !callerId.equals(req.getRequesterId()) &&
                !callerId.equals(req.getTargetUserId());

        boolean canCancel = req.getStatus() == PrivilegedRequestStatus.PENDING &&
                callerId.equals(req.getRequesterId());

        boolean canExecute = req.getStatus() == PrivilegedRequestStatus.APPROVED &&
                (callerId.equals(req.getRequesterId()) || callerId.equals(req.getTargetUserId()));

        boolean canRevoke = (req.getStatus() == PrivilegedRequestStatus.APPROVED || req.getStatus() == PrivilegedRequestStatus.EXECUTED);

        return PrivilegedAccessRequestResponse.of(
                req,
                requester != null ? requester.getEmail() : null,
                requester != null ? requester.getFullName() : null,
                target != null ? target.getEmail() : null,
                target != null ? target.getFullName() : null,
                project != null ? project.getName() : null,
                env != null ? env.getName() : null,
                secret != null ? secret.getName() : null,
                approvalResponses,
                canApprove,
                canCancel,
                canExecute,
                canRevoke
        );
    }

    private PrivilegedAccessElevationResponse buildElevationResponse(PrivilegedAccessElevation elev, Instant now) {
        User user = userRepository.findById(elev.getUserId()).orElse(null);
        Project project = elev.getProjectId() != null ? projectRepository.findById(elev.getProjectId()).orElse(null) : null;
        Environment env = elev.getEnvironmentId() != null ? environmentRepository.findById(elev.getEnvironmentId()).orElse(null) : null;
        Secret secret = elev.getSecretId() != null ? secretRepository.findById(elev.getSecretId()).orElse(null) : null;

        return PrivilegedAccessElevationResponse.of(
                elev,
                user != null ? user.getEmail() : null,
                user != null ? user.getFullName() : null,
                project != null ? project.getName() : null,
                env != null ? env.getName() : null,
                secret != null ? secret.getName() : null,
                now
        );
    }

    private PrivilegedAccessPolicyResponse buildPolicyResponse(PrivilegedAccessPolicy policy) {
        Project project = policy.getProjectId() != null ? projectRepository.findById(policy.getProjectId()).orElse(null) : null;
        Environment env = policy.getEnvironmentId() != null ? environmentRepository.findById(policy.getEnvironmentId()).orElse(null) : null;
        Secret secret = policy.getSecretId() != null ? secretRepository.findById(policy.getSecretId()).orElse(null) : null;

        return PrivilegedAccessPolicyResponse.of(
                policy,
                project != null ? project.getName() : null,
                env != null ? env.getName() : null,
                secret != null ? secret.getName() : null
        );
    }
}
