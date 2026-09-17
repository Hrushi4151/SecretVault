package com.secretvault.workspace.invitation.service;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.invitation.dto.AcceptInvitationRequest;
import com.secretvault.workspace.invitation.dto.CreateInvitationRequest;
import com.secretvault.workspace.invitation.dto.InvitationResponse;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing secure workspace invitations and token-based onboarding.
 */
@Service
public class WorkspaceInvitationService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInvitationService.class);

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public WorkspaceInvitationService(
            WorkspaceInvitationRepository invitationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.invitationRepository = invitationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
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

        if (!workspaceRepository.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace not found");
        }

        String targetEmail = request.email().trim().toLowerCase(Locale.ROOT);

        // Check if user is already a member
        Optional<User> existingUser = userRepository.findByEmail(targetEmail);
        if (existingUser.isPresent() && membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, existingUser.get().getId())) {
            throw ApiException.conflict("User is already an active member of this workspace");
        }

        // Check for duplicate pending invitation
        if (invitationRepository.existsByWorkspaceIdAndEmailAndStatus(workspaceId, targetEmail, InvitationStatus.PENDING)) {
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
     * Accepts a workspace invitation using a one-time secret token.
     */
    @Transactional
    public WorkspaceResponse acceptInvitation(AcceptInvitationRequest request, UUID acceptingUserId) {
        String tokenHash = hashToken(request.token().trim());

        WorkspaceInvitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired invitation token"));

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
        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspace.getId(), acceptingUserId)
                .orElseGet(() -> {
                    WorkspaceMembership newMem = new WorkspaceMembership(workspace.getId(), acceptingUserId, invitation.getRole());
                    return membershipRepository.save(newMem);
                });

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("User [{}] successfully accepted invitation [{}] for workspace [{}] with role [{}]",
                acceptingUserId, invitation.getId(), workspace.getId(), invitation.getRole());

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

        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRevokedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("Revoked invitation [{}] on workspace [{}] by actor [{}]",
                invitationId, workspaceId, actorUserId);
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
