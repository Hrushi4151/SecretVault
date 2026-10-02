package com.secretvault.workspace.invitation.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.invitation.dto.AcceptInvitationRequest;
import com.secretvault.workspace.invitation.dto.CreateInvitationRequest;
import com.secretvault.workspace.invitation.dto.InvitationResponse;
import com.secretvault.workspace.invitation.dto.UserInvitationItemResponse;
import com.secretvault.workspace.invitation.dto.UserInvitationsListResponse;
import com.secretvault.workspace.invitation.dto.UserLookupResponse;
import com.secretvault.workspace.invitation.entity.InvitationStatus;
import com.secretvault.workspace.invitation.entity.WorkspaceInvitation;
import com.secretvault.workspace.invitation.repository.WorkspaceInvitationRepository;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing secure workspace invitations, in-app delivery, user lookup, and token/in-app onboarding.
 */
@Service
public class WorkspaceInvitationService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInvitationService.class);

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public WorkspaceInvitationService(
            WorkspaceInvitationRepository invitationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.invitationRepository = invitationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Authenticated lookup to inspect whether an email belongs to an existing user and check membership/pending state.
     * Scoped to authorized workspace managers (OWNER/ADMIN) to prevent arbitrary user enumeration.
     */
    @Transactional(readOnly = true)
    public UserLookupResponse lookupUser(UUID workspaceId, String email, UUID actorUserId) {
        WorkspaceMembership actorMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        if (!actorMembership.getRole().canManageWorkspace()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can lookup users for invitation");
        }

        if (email == null || email.trim().isEmpty()) {
            return UserLookupResponse.notFound(false, false);
        }

        String targetEmail = normalizeEmail(email);
        Optional<User> existingUserOpt = userRepository.findByEmail(targetEmail);

        boolean isMember = false;
        if (existingUserOpt.isPresent()) {
            isMember = membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, existingUserOpt.get().getId());
        }

        boolean hasPending = invitationRepository.existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(workspaceId, targetEmail, InvitationStatus.PENDING);

        if (existingUserOpt.isPresent()) {
            User user = existingUserOpt.get();
            return UserLookupResponse.found(user.getId(), user.getFullName(), user.getEmail(), isMember, hasPending);
        } else {
            return UserLookupResponse.notFound(isMember, hasPending);
        }
    }

    /**
     * Creates a new pending workspace invitation and generates a single-use cryptographically random token.
     * Requires OWNER or ADMIN role on the workspace.
     */
    @Transactional
    public InvitationResponse createInvitation(UUID workspaceId, CreateInvitationRequest request, UUID actorUserId) {
        WorkspaceMembership actorMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        if (!actorMembership.getRole().canManageWorkspace()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can invite new members to this workspace");
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace not found"));

        String targetEmail = normalizeEmail(request.email());

        // Check if user is already a member
        Optional<User> existingUser = userRepository.findByEmail(targetEmail);
        if (existingUser.isPresent() && membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, existingUser.get().getId())) {
            throw ApiException.conflict("User is already an active member of this workspace");
        }

        // Check for duplicate pending invitation
        if (invitationRepository.existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(workspaceId, targetEmail, InvitationStatus.PENDING)) {
            throw ApiException.conflict("A pending invitation already exists for this email address");
        }

        int validityDays = request.expiresInDays() != null ? request.expiresInDays() : 7;
        Instant expiresAt = Instant.now().plus(Duration.ofDays(validityDays));

        String rawToken = "inv_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);

        WorkspaceInvitation invitation = new WorkspaceInvitation(
                workspaceId,
                targetEmail,
                actorUserId,
                request.role(),
                tokenHash,
                expiresAt
        );
        invitation = invitationRepository.save(invitation);

        auditService.recordAudit(
                workspace.getOrganizationId(),
                workspaceId,
                actorUserId,
                "USER",
                AuditAction.INVITATION_CREATED,
                "WORKSPACE_INVITATION",
                invitation.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Created invitation [{}] for email [{}] with role [{}] on workspace [{}] by actor [{}]",
                invitation.getId(), targetEmail, request.role(), workspaceId, actorUserId);

        return InvitationResponse.fromEntity(invitation, rawToken);
    }

    /**
     * Lists all pending invitations for a workspace.
     */
    @Transactional(readOnly = true)
    public List<InvitationResponse> getPendingInvitations(UUID workspaceId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }

        return invitationRepository.findByWorkspaceIdAndStatus(workspaceId, InvitationStatus.PENDING)
                .stream()
                .filter(inv -> !inv.isExpired())
                .map(inv -> InvitationResponse.fromEntity(inv, null))
                .toList();
    }

    /**
     * Retrieves all active pending invitations for the authenticated user based on their registered email.
     * Powers in-app delivery without requiring external email providers.
     */
    @Transactional
    public UserInvitationsListResponse getMyPendingInvitations(UUID authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        String userEmail = normalizeEmail(user.getEmail());
        List<WorkspaceInvitation> invitations = invitationRepository.findByEmailIgnoreCaseAndStatus(userEmail, InvitationStatus.PENDING);

        List<UserInvitationItemResponse> items = new ArrayList<>();
        Instant now = Instant.now();

        for (WorkspaceInvitation inv : invitations) {
            if (inv.getExpiresAt().isBefore(now)) {
                inv.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(inv);
                continue;
            }

            Optional<Workspace> wsOpt = workspaceRepository.findById(inv.getWorkspaceId());
            String workspaceName = wsOpt.map(Workspace::getName).orElse("Workspace");

            UserInvitationItemResponse.InviterSummary inviterSummary = null;
            if (inv.getInvitedBy() != null) {
                Optional<User> inviterOpt = userRepository.findById(inv.getInvitedBy());
                if (inviterOpt.isPresent()) {
                    User inviter = inviterOpt.get();
                    inviterSummary = new UserInvitationItemResponse.InviterSummary(inviter.getId(), inviter.getFullName(), inviter.getEmail());
                }
            }

            items.add(new UserInvitationItemResponse(
                    inv.getId(),
                    inv.getWorkspaceId(),
                    workspaceName,
                    inviterSummary,
                    inv.getRole(),
                    inv.getStatus(),
                    inv.getExpiresAt(),
                    inv.getCreatedAt()
            ));
        }

        return new UserInvitationsListResponse(items);
    }

    /**
     * Accepts a workspace invitation in-app directly by invitation ID.
     * Enforces target identity check (preventing User C from accepting User B's invite) and pessimistic concurrency lock.
     */
    @Transactional
    public WorkspaceResponse acceptInvitationById(UUID invitationId, UUID acceptingUserId) {
        User acceptingUser = userRepository.findById(acceptingUserId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        WorkspaceInvitation invitation = invitationRepository.findWithLockById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));

        return processInvitationAcceptance(invitation, acceptingUser);
    }

    /**
     * Declines a workspace invitation in-app by invitation ID.
     */
    @Transactional
    public void declineInvitationById(UUID invitationId, UUID acceptingUserId) {
        User acceptingUser = userRepository.findById(acceptingUserId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        WorkspaceInvitation invitation = invitationRepository.findWithLockById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));

        if (!normalizeEmail(invitation.getEmail()).equals(normalizeEmail(acceptingUser.getEmail()))) {
            throw ApiException.forbidden("This invitation was issued to a different email address");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.badRequest("Invitation is no longer pending (status: " + invitation.getStatus() + ")");
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        invitationRepository.save(invitation);

        Workspace workspace = workspaceRepository.findById(invitation.getWorkspaceId()).orElse(null);
        if (workspace != null) {
            auditService.recordAudit(
                    workspace.getOrganizationId(),
                    workspace.getId(),
                    acceptingUserId,
                    "USER",
                    AuditAction.INVITATION_DECLINED,
                    "WORKSPACE_INVITATION",
                    invitation.getId(),
                    null,
                    null,
                    "SUCCESS"
            );
        }

        log.info("User [{}] declined invitation [{}] for workspace [{}]",
                acceptingUserId, invitation.getId(), invitation.getWorkspaceId());
    }

    /**
     * Accepts a workspace invitation using a one-time secret token.
     */
    @Transactional
    public WorkspaceResponse acceptInvitation(AcceptInvitationRequest request, UUID acceptingUserId) {
        User acceptingUser = userRepository.findById(acceptingUserId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        String tokenHash = hashToken(request.token().trim());
        WorkspaceInvitation invitation = invitationRepository.findWithLockByTokenHash(tokenHash)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired invitation token"));

        return processInvitationAcceptance(invitation, acceptingUser);
    }

    private WorkspaceResponse processInvitationAcceptance(WorkspaceInvitation invitation, User acceptingUser) {
        // Target Identity Validation (Enforce normalized email match)
        if (!normalizeEmail(invitation.getEmail()).equals(normalizeEmail(acceptingUser.getEmail()))) {
            log.warn("Security rejection: User [{}] (email [{}]) attempted to accept invitation [{}] issued to [{}]",
                    acceptingUser.getId(), acceptingUser.getEmail(), invitation.getId(), invitation.getEmail());
            throw ApiException.forbidden("This invitation was issued to a different email address");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.badRequest("Invitation is no longer active (status: " + invitation.getStatus() + ")");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw ApiException.badRequest("Invitation has expired");
        }

        Workspace workspace = workspaceRepository.findById(invitation.getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Target workspace no longer exists"));

        // Grant workspace membership if not already present
        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspace.getId(), acceptingUser.getId())
                .orElseGet(() -> {
                    WorkspaceMembership newMem = new WorkspaceMembership(workspace.getId(), acceptingUser.getId(), invitation.getRole());
                    return membershipRepository.save(newMem);
                });

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        auditService.recordAudit(
                workspace.getOrganizationId(),
                workspace.getId(),
                acceptingUser.getId(),
                "USER",
                AuditAction.INVITATION_ACCEPTED,
                "WORKSPACE_INVITATION",
                invitation.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("User [{}] successfully accepted invitation [{}] for workspace [{}] with role [{}]",
                acceptingUser.getId(), invitation.getId(), workspace.getId(), invitation.getRole());

        return WorkspaceResponse.fromEntity(workspace, membership.getRole());
    }

    /**
     * Revokes a pending workspace invitation.
     * Requires OWNER or ADMIN role on the workspace.
     */
    @Transactional
    public void revokeInvitation(UUID workspaceId, UUID invitationId, UUID actorUserId) {
        WorkspaceMembership actorMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        if (!actorMembership.getRole().canManageWorkspace()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can revoke workspace invitations");
        }

        WorkspaceInvitation invitation = invitationRepository.findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Invitation not found in this workspace"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.badRequest("Cannot revoke invitation with status: " + invitation.getStatus());
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace not found"));

        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRevokedAt(Instant.now());
        invitationRepository.save(invitation);

        auditService.recordAudit(
                workspace.getOrganizationId(),
                workspaceId,
                actorUserId,
                "USER",
                AuditAction.INVITATION_REVOKED,
                "WORKSPACE_INVITATION",
                invitation.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Revoked invitation [{}] on workspace [{}] by actor [{}]",
                invitationId, workspaceId, actorUserId);
    }

    private String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
