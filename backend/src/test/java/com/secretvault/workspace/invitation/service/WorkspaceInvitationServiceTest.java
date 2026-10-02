package com.secretvault.workspace.invitation.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
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
import com.secretvault.workspace.invitation.dto.UserInvitationsListResponse;
import com.secretvault.workspace.invitation.dto.UserLookupResponse;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private AuditService auditService;

    @InjectMocks
    private WorkspaceInvitationService invitationService;

    private UUID workspaceId;
    private UUID organizationId;
    private UUID actorUserId;
    private UUID targetUserId;
    private Workspace testWorkspace;
    private WorkspaceMembership ownerMembership;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        testWorkspace = new Workspace(organizationId, "Core Workspace", "core-ws", false);
        testWorkspace.setId(workspaceId);

        ownerMembership = new WorkspaceMembership(workspaceId, actorUserId, WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("Should create invitation with single-use raw token and hash storage")
    void testCreateInvitationSuccess() {
        CreateInvitationRequest request = new CreateInvitationRequest("alice@example.com", WorkspaceRole.DEVELOPER, 7);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(invitationRepository.existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(workspaceId, "alice@example.com", InvitationStatus.PENDING))
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
        verify(auditService).recordAudit(eq(organizationId), eq(workspaceId), eq(actorUserId), any(), eq(AuditAction.INVITATION_CREATED), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("Should reject invitation if user is already a member")
    void testCreateInvitationAlreadyMember() {
        CreateInvitationRequest request = new CreateInvitationRequest("bob@example.com", WorkspaceRole.DEVELOPER, 7);
        User bob = new User("bob@example.com", "hash", "Bob Builder");
        bob.setId(targetUserId);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(bob));
        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                invitationService.createInvitation(workspaceId, request, actorUserId));

        assertEquals(409, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("already an active member"));
    }

    @Test
    @DisplayName("Should reject duplicate pending invitation for same email")
    void testCreateInvitationDuplicatePending() {
        CreateInvitationRequest request = new CreateInvitationRequest("charlie@example.com", WorkspaceRole.DEVELOPER, 7);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.empty());
        when(invitationRepository.existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(workspaceId, "charlie@example.com", InvitationStatus.PENDING))
                .thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                invitationService.createInvitation(workspaceId, request, actorUserId));

        assertEquals(409, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("pending invitation already exists"));
    }

    @Test
    @DisplayName("Should lookup user and return found state, member status, and pending invitation status")
    void testLookupUserStates() {
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId))
                .thenReturn(Optional.of(ownerMembership));

        User existingUser = new User("hrushikesh@example.com", "hash", "Hrushikesh More");
        existingUser.setId(targetUserId);

        // Case 1: Found, not member, no pending invite
        when(userRepository.findByEmail("hrushikesh@example.com")).thenReturn(Optional.of(existingUser));
        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(false);
        when(invitationRepository.existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(workspaceId, "hrushikesh@example.com", InvitationStatus.PENDING))
                .thenReturn(false);

        UserLookupResponse res1 = invitationService.lookupUser(workspaceId, "hrushikesh@example.com", actorUserId);
        assertTrue(res1.exists());
        assertEquals("Hrushikesh More", res1.user().name());
        assertFalse(res1.isMember());
        assertFalse(res1.hasPendingInvitation());

        // Case 2: Not found
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(invitationRepository.existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(workspaceId, "unknown@example.com", InvitationStatus.PENDING))
                .thenReturn(false);

        UserLookupResponse res2 = invitationService.lookupUser(workspaceId, "unknown@example.com", actorUserId);
        assertFalse(res2.exists());
        assertNull(res2.user());
        assertFalse(res2.isMember());
    }

    @Test
    @DisplayName("Should deliver in-app pending invitations for authenticated user")
    void testGetMyPendingInvitations() {
        User user = new User("recipient@example.com", "hash", "Recipient User");
        user.setId(targetUserId);

        WorkspaceInvitation inv1 = new WorkspaceInvitation(
                workspaceId, "recipient@example.com", actorUserId, WorkspaceRole.DEVELOPER, "hash1",
                Instant.now().plus(5, ChronoUnit.DAYS)
        );
        inv1.setId(UUID.randomUUID());

        User inviter = new User("inviter@example.com", "hash", "Inviter Admin");
        inviter.setId(actorUserId);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(invitationRepository.findByEmailIgnoreCaseAndStatus("recipient@example.com", InvitationStatus.PENDING))
                .thenReturn(List.of(inv1));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(inviter));

        UserInvitationsListResponse res = invitationService.getMyPendingInvitations(targetUserId);

        assertNotNull(res);
        assertEquals(1, res.items().size());
        assertEquals(workspaceId, res.items().get(0).workspaceId());
        assertEquals("Core Workspace", res.items().get(0).workspaceName());
        assertEquals("Inviter Admin", res.items().get(0).invitedBy().name());
    }

    @Test
    @DisplayName("Should accept in-app invitation by ID and create workspace membership")
    void testAcceptInvitationByIdSuccess() {
        UUID invitationId = UUID.randomUUID();
        User recipient = new User("alice@example.com", "hash", "Alice Smith");
        recipient.setId(targetUserId);

        WorkspaceInvitation inv = new WorkspaceInvitation(
                workspaceId, "alice@example.com", actorUserId, WorkspaceRole.DEVELOPER, "hash1",
                Instant.now().plus(5, ChronoUnit.DAYS)
        );
        inv.setId(invitationId);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(recipient));
        when(invitationRepository.findWithLockById(invitationId)).thenReturn(Optional.of(inv));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(WorkspaceMembership.class))).thenAnswer(m -> m.getArgument(0));

        WorkspaceResponse wsRes = invitationService.acceptInvitationById(invitationId, targetUserId);

        assertNotNull(wsRes);
        assertEquals(WorkspaceRole.DEVELOPER, wsRes.role());
        assertEquals(InvitationStatus.ACCEPTED, inv.getStatus());
        assertNotNull(inv.getAcceptedAt());
        verify(auditService).recordAudit(eq(organizationId), eq(workspaceId), eq(targetUserId), any(), eq(AuditAction.INVITATION_ACCEPTED), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("Security: Target identity mismatch must block unauthorized user acceptance")
    void testAcceptInvitationByIdWrongEmailBlocked() {
        UUID invitationId = UUID.randomUUID();
        User maliciousUser = new User("mallory@attacker.com", "hash", "Mallory");
        UUID malloryId = UUID.randomUUID();
        maliciousUser.setId(malloryId);

        WorkspaceInvitation inv = new WorkspaceInvitation(
                workspaceId, "victim@target.com", actorUserId, WorkspaceRole.DEVELOPER, "hash1",
                Instant.now().plus(5, ChronoUnit.DAYS)
        );
        inv.setId(invitationId);

        when(userRepository.findById(malloryId)).thenReturn(Optional.of(maliciousUser));
        when(invitationRepository.findWithLockById(invitationId)).thenReturn(Optional.of(inv));

        ApiException ex = assertThrows(ApiException.class, () ->
                invitationService.acceptInvitationById(invitationId, malloryId));

        assertEquals(403, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("different email address"));
        assertEquals(InvitationStatus.PENDING, inv.getStatus());
    }

    @Test
    @DisplayName("Should decline in-app invitation by ID")
    void testDeclineInvitationByIdSuccess() {
        UUID invitationId = UUID.randomUUID();
        User recipient = new User("alice@example.com", "hash", "Alice Smith");
        recipient.setId(targetUserId);

        WorkspaceInvitation inv = new WorkspaceInvitation(
                workspaceId, "alice@example.com", actorUserId, WorkspaceRole.DEVELOPER, "hash1",
                Instant.now().plus(5, ChronoUnit.DAYS)
        );
        inv.setId(invitationId);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(recipient));
        when(invitationRepository.findWithLockById(invitationId)).thenReturn(Optional.of(inv));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));

        invitationService.declineInvitationById(invitationId, targetUserId);

        assertEquals(InvitationStatus.DECLINED, inv.getStatus());
        verify(auditService).recordAudit(eq(organizationId), eq(workspaceId), eq(targetUserId), any(), eq(AuditAction.INVITATION_DECLINED), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("Should reject acceptance of expired invitation")
    void testAcceptExpiredInvitationFails() {
        UUID invitationId = UUID.randomUUID();
        User recipient = new User("alice@example.com", "hash", "Alice Smith");
        recipient.setId(targetUserId);

        WorkspaceInvitation inv = new WorkspaceInvitation(
                workspaceId, "alice@example.com", actorUserId, WorkspaceRole.DEVELOPER, "hash1",
                Instant.now().minus(2, ChronoUnit.DAYS)
        );
        inv.setId(invitationId);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(recipient));
        when(invitationRepository.findWithLockById(invitationId)).thenReturn(Optional.of(inv));

        ApiException ex = assertThrows(ApiException.class, () ->
                invitationService.acceptInvitationById(invitationId, targetUserId));

        assertEquals(400, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Should revoke invitation when actor is OWNER/ADMIN")
    void testRevokeInvitationSuccess() {
        UUID invitationId = UUID.randomUUID();
        WorkspaceInvitation inv = new WorkspaceInvitation(
                workspaceId, "dave@example.com", actorUserId, WorkspaceRole.DEVELOPER, "hash1",
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        inv.setId(invitationId);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId))
                .thenReturn(Optional.of(ownerMembership));
        when(invitationRepository.findByIdAndWorkspaceId(invitationId, workspaceId))
                .thenReturn(Optional.of(inv));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));

        invitationService.revokeInvitation(workspaceId, invitationId, actorUserId);

        assertEquals(InvitationStatus.REVOKED, inv.getStatus());
        assertNotNull(inv.getRevokedAt());
        verify(auditService).recordAudit(eq(organizationId), eq(workspaceId), eq(actorUserId), any(), eq(AuditAction.INVITATION_REVOKED), any(), any(), any(), any(), eq("SUCCESS"));
    }
}
