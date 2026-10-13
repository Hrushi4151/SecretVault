package com.secretvault.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.ai.tool.domain.UserTools;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.invitation.dto.UserInvitationItemResponse;
import com.secretvault.workspace.invitation.dto.UserInvitationsListResponse;
import com.secretvault.workspace.invitation.entity.InvitationStatus;
import com.secretvault.workspace.invitation.service.WorkspaceInvitationService;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UserToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserRepository userRepository;
    private WorkspaceMembershipRepository membershipRepository;
    private WorkspaceRepository workspaceRepository;
    private WorkspaceInvitationService invitationService;
    private SecurityFindingRepository findingRepository;
    private AuditLogRepository auditLogRepository;
    private JitAccessRequestRepository jitRepository;

    private UUID workspaceId;
    private UUID userId;
    private AiToolInvocationContext context;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        membershipRepository = Mockito.mock(WorkspaceMembershipRepository.class);
        workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        invitationService = Mockito.mock(WorkspaceInvitationService.class);
        findingRepository = Mockito.mock(SecurityFindingRepository.class);
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        jitRepository = Mockito.mock(JitAccessRequestRepository.class);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new AiToolInvocationContext(
                workspaceId,
                userId,
                "alice@acme.corp",
                "DEVELOPER",
                Set.of(),
                UUID.randomUUID(),
                "corr-123"
        );
    }

    @Test
    @DisplayName("user.getCurrent should return authenticated user profile and memberships")
    void testUserGetCurrentTool() {
        User user = new User("alice@acme.corp", "hash", "Alice Smith");
        user.setId(userId);
        user.setMfaEnabled(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        WorkspaceMembership mem = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(mem));

        Workspace ws = new Workspace(UUID.randomUUID(), "Acme Security", "acme-sec", true);
        ws.setId(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(ws));

        UserTools.UserGetCurrentTool tool = new UserTools.UserGetCurrentTool(
                userRepository,
                membershipRepository,
                workspaceRepository,
                objectMapper
        );

        AiToolResult result = tool.execute(context, Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.outputJson()).contains("alice@acme.corp");
        assertThat(result.outputJson()).contains("Alice Smith");
        assertThat(result.outputJson()).contains("Acme Security");
    }

    @Test
    @DisplayName("user.invitations should return actual pending invitations from real service")
    void testUserInvitationsTool() {
        UUID invId1 = UUID.randomUUID();
        UUID invId2 = UUID.randomUUID();

        UserInvitationItemResponse item1 = new UserInvitationItemResponse(
                invId1,
                workspaceId,
                "Acme Security Workspace",
                new UserInvitationItemResponse.InviterSummary(UUID.randomUUID(), "Admin Bob", "admin@acme.corp"),
                WorkspaceRole.DEVELOPER,
                InvitationStatus.PENDING,
                Instant.now().plus(7, ChronoUnit.DAYS),
                Instant.now()
        );

        UserInvitationItemResponse item2 = new UserInvitationItemResponse(
                invId2,
                UUID.randomUUID(),
                "Payments Platform",
                null,
                WorkspaceRole.VIEWER,
                InvitationStatus.PENDING,
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now()
        );

        when(invitationService.getMyPendingInvitations(userId))
                .thenReturn(new UserInvitationsListResponse(List.of(item1, item2)));

        UserTools.UserInvitationsTool tool = new UserTools.UserInvitationsTool(invitationService, objectMapper);
        AiToolResult result = tool.execute(context, Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.outputJson()).contains("Acme Security Workspace");
        assertThat(result.outputJson()).contains("Payments Platform");
        assertThat(result.outputJson()).contains("DEVELOPER");
        assertThat(result.outputJson()).contains("VIEWER");
        assertThat(result.outputJson()).contains("/workspaces/invitations");
        assertThat(result.outputJson()).contains("OPEN_INVITATION");
    }

    @Test
    @DisplayName("user.invitations should return empty list gracefully when no invitations exist")
    void testUserInvitationsEmpty() {
        when(invitationService.getMyPendingInvitations(userId))
                .thenReturn(new UserInvitationsListResponse(List.of()));

        UserTools.UserInvitationsTool tool = new UserTools.UserInvitationsTool(invitationService, objectMapper);
        AiToolResult result = tool.execute(context, Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.outputJson()).contains("\"totalPendingInvitations\":0");
    }

    @Test
    @DisplayName("user.notifications should aggregate invitations and security findings")
    void testUserNotificationsTool() {
        when(invitationService.getMyPendingInvitations(userId))
                .thenReturn(new UserInvitationsListResponse(List.of(
                        new UserInvitationItemResponse(
                                UUID.randomUUID(),
                                workspaceId,
                                "Test WS",
                                null,
                                WorkspaceRole.DEVELOPER,
                                InvitationStatus.PENDING,
                                Instant.now().plus(3, ChronoUnit.DAYS),
                                Instant.now()
                        )
                )));

        when(findingRepository.countByWorkspaceIdAndStatusIn(eq(workspaceId), any()))
                .thenReturn(2L);

        UserTools.UserNotificationsTool tool = new UserTools.UserNotificationsTool(
                invitationService,
                findingRepository,
                objectMapper
        );

        AiToolResult result = tool.execute(context, Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.outputJson()).contains("pendingInvitationsCount\":1");
        assertThat(result.outputJson()).contains("openSecurityFindingsCount\":2");
    }

    @Test
    @DisplayName("user.activity should return user-specific audit logs")
    void testUserActivityTool() {
        AuditLog log1 = new AuditLog(
                UUID.randomUUID(),
                workspaceId,
                userId,
                "USER",
                AuditAction.SECRET_ROTATION_TRIGGERED,
                "SECRET",
                UUID.randomUUID(),
                "req-1",
                "127.0.0.1",
                "SUCCESS"
        );

        when(auditLogRepository.findByActorIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(log1));

        UserTools.UserActivityTool tool = new UserTools.UserActivityTool(auditLogRepository, objectMapper);
        AiToolResult result = tool.execute(context, Map.of("limit", 5));

        assertThat(result.success()).isTrue();
        assertThat(result.outputJson()).contains("SECRET_ROTATION_TRIGGERED");
        assertThat(result.outputJson()).contains("SUCCESS");
    }
}
