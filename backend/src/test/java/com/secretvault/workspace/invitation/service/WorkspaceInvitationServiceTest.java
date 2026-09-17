package com.secretvault.workspace.invitation.service;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.invitation.dto.AcceptInvitationRequest;
import com.secretvault.workspace.invitation.dto.CreateInvitationRequest;
import com.secretvault.workspace.invitation.dto.InvitationResponse;
import com.secretvault.workspace.invitation.entity.InvitationStatus;
import com.secretvault.workspace.invitation.entity.WorkspaceInvitation;
import com.secretvault.workspace.invitation.repository.WorkspaceInvitationRepository;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceInvitationServiceTest {

    @Mock
    private WorkspaceInvitationRepository invitationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WorkspaceInvitationService invitationService;

    private UUID workspaceId;
    private UUID actorUserId;
    private UUID targetUserId;
    private Workspace testWorkspace;
    private WorkspaceMembership ownerMembership;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        testWorkspace = new Workspace(UUID.randomUUID(), "Core Workspace", "core-ws", false);
        testWorkspace.setId(workspaceId);

        ownerMembership = new WorkspaceMembership(workspaceId, actorUserId, WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("Should create invitation with single-use raw token and hash storage")
    void testCreateInvitationSuccess() {
        CreateInvitationRequest request = new CreateInvitationRequest("alice@example.com", WorkspaceRole.DEVELOPER, 7);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(invitationRepository.existsByWorkspaceIdAndEmailAndStatus(workspaceId, "alice@example.com", InvitationStatus.PENDING))
                .thenReturn(false);
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(inv -> {
            WorkspaceInvitation wi = inv.getArgument(0);
            wi.setId(UUID.randomUUID());
            return wi;
        });

        InvitationResponse response = invitationService.createInvitation(workspaceId, request, actorUserId);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
        assertEquals(WorkspaceRole.DEVELOPER, response.role());
        assertEquals(InvitationStatus.PENDING, response.status());
        assertNotNull(response.rawToken());
        assertTrue(response.rawToken().startsWith("inv_"));
    }

    @Test
    @DisplayName("Should forbid non-owner/non-admin from creating invitations")
    void testCreateInvitationForbiddenForDeveloper() {
        WorkspaceMembership devMem = new WorkspaceMembership(workspaceId, actorUserId, WorkspaceRole.DEVELOPER);
        CreateInvitationRequest request = new CreateInvitationRequest("alice@example.com", WorkspaceRole.DEVELOPER, 7);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId))
                .thenReturn(Optional.of(devMem));

        ApiException ex = assertThrows(ApiException.class, () ->
                invitationService.createInvitation(workspaceId, request, actorUserId));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should accept valid invitation token and create workspace membership")
    void testAcceptInvitationSuccess() throws Exception {
        String rawToken = "inv_testtoken123456789";
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        String tokenHash = HexFormat.of().formatHex(hash);

        WorkspaceInvitation invitation = new WorkspaceInvitation(
                workspaceId,
                "alice@example.com",
                actorUserId,
                WorkspaceRole.DEVELOPER,
                tokenHash,
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        invitation.setId(UUID.randomUUID());

        when(invitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invitation));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(WorkspaceMembership.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponse response = invitationService.acceptInvitation(new AcceptInvitationRequest(rawToken), targetUserId);

        assertNotNull(response);
        assertEquals(workspaceId, response.id());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
        assertNotNull(invitation.getAcceptedAt());
        verify(membershipRepository).save(any(WorkspaceMembership.class));
    }

    @Test
    @DisplayName("Should reject expired invitation")
    void testAcceptInvitationExpired() throws Exception {
        String rawToken = "inv_expiredtoken123456789";
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        String tokenHash = HexFormat.of().formatHex(hash);

        WorkspaceInvitation expiredInvitation = new WorkspaceInvitation(
                workspaceId,
                "alice@example.com",
                actorUserId,
                WorkspaceRole.DEVELOPER,
                tokenHash,
                Instant.now().minus(1, ChronoUnit.DAYS)
        );

        when(invitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredInvitation));

        ApiException ex = assertThrows(ApiException.class, () ->
                invitationService.acceptInvitation(new AcceptInvitationRequest(rawToken), targetUserId));
        assertEquals("BAD_REQUEST", ex.getCode());
        assertEquals(InvitationStatus.EXPIRED, expiredInvitation.getStatus());
    }

    @Test
    @DisplayName("Should revoke pending invitation")
    void testRevokeInvitationSuccess() {
        UUID invitationId = UUID.randomUUID();
        WorkspaceInvitation invitation = new WorkspaceInvitation(
                workspaceId,
                "alice@example.com",
                actorUserId,
                WorkspaceRole.DEVELOPER,
                "hash123",
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        invitation.setId(invitationId);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(invitationRepository.findByIdAndWorkspaceId(invitationId, workspaceId))
                .thenReturn(Optional.of(invitation));

        invitationService.revokeInvitation(workspaceId, invitationId, actorUserId);

        assertEquals(InvitationStatus.REVOKED, invitation.getStatus());
        assertNotNull(invitation.getRevokedAt());
    }
}
