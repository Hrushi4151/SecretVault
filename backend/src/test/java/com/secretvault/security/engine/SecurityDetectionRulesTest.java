package com.secretvault.security.engine;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.repository.AccessReviewCampaignRepository;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.security.engine.rules.*;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityDetectionRulesTest {

    @Mock
    private WorkspaceMembershipRepository membershipRepository;
    @Mock
    private ProjectAccessRepository projectAccessRepository;
    @Mock
    private EnvironmentAccessRepository environmentAccessRepository;
    @Mock
    private AccessGrantRepository accessGrantRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private SecurityEventRepository eventRepository;
    @Mock
    private JitAccessRequestRepository jitRepository;
    @Mock
    private AccessReviewCampaignRepository campaignRepository;

    private final UUID orgId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace(orgId, "Security Test Workspace", "sec-ws", false);
        workspace.setId(workspaceId);
    }

    @Test
    @DisplayName("ExcessivePrivilegeRule triggers when non-admin user accumulates elevated permissions")
    void excessivePrivilegeRule_triggersOnElevatedScope() {
        ExcessivePrivilegeRule rule = new ExcessivePrivilegeRule(
                membershipRepository, projectAccessRepository, environmentAccessRepository,
                accessGrantRepository, projectRepository
        );

        WorkspaceMembership member = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        when(membershipRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(member));

        ProjectAccess pa1 = new ProjectAccess(UUID.randomUUID(), userId, WorkspaceRole.ADMIN, userId);
        ProjectAccess pa2 = new ProjectAccess(UUID.randomUUID(), userId, WorkspaceRole.ADMIN, userId);
        when(projectAccessRepository.findByUserId(userId)).thenReturn(List.of(pa1, pa2));

        EnvironmentAccess ea = new EnvironmentAccess(UUID.randomUUID(), userId, PermissionLevel.MANAGE, userId);
        when(environmentAccessRepository.findByUserId(userId)).thenReturn(List.of(ea));

        when(accessGrantRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(List.of());

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.EXCESSIVE_PRIVILEGE);
        assertThat(findings.get(0).severity()).isEqualTo(FindingSeverity.MEDIUM);
    }

    @Test
    @DisplayName("PrivilegeEscalationRule triggers on recent elevation followed by secret reveals")
    void privilegeEscalationRule_triggersOnElevationAndSecretReveals() {
        PrivilegeEscalationRule rule = new PrivilegeEscalationRule(eventRepository);

        SecurityEvent grantEvent = new SecurityEvent(
                workspaceId, null, null, userId,
                SecurityEventType.ACCESS_GRANT_CREATED, SecurityEventSeverity.INFO,
                SecurityEventOutcome.SUCCESS, "API", null, null, null, "{}"
        );
        SecurityEvent reveal1 = new SecurityEvent(
                workspaceId, null, null, userId,
                SecurityEventType.SECRET_REVEALED, SecurityEventSeverity.INFO,
                SecurityEventOutcome.SUCCESS, "API", null, null, null, "{}"
        );
        SecurityEvent reveal2 = new SecurityEvent(
                workspaceId, null, null, userId,
                SecurityEventType.SECRET_REVEALED, SecurityEventSeverity.INFO,
                SecurityEventOutcome.SUCCESS, "API", null, null, null, "{}"
        );
        SecurityEvent reveal3 = new SecurityEvent(
                workspaceId, null, null, userId,
                SecurityEventType.SECRET_REVEALED, SecurityEventSeverity.INFO,
                SecurityEventOutcome.SUCCESS, "API", null, null, null, "{}"
        );

        when(eventRepository.findRecentEvents(eq(workspaceId), any())).thenReturn(List.of(grantEvent, reveal1, reveal2, reveal3));

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.PRIVILEGE_ESCALATION_PATTERN);
    }

    @Test
    @DisplayName("SuspiciousJitRule triggers on rapid repeated or rejected JIT requests")
    void suspiciousJitRule_triggersOnFrequentRequests() {
        SuspiciousJitRule rule = new SuspiciousJitRule(jitRepository);

        UUID envId = UUID.randomUUID();
        JitAccessRequest req1 = new JitAccessRequest(workspaceId, userId, null, envId, null, AccessPermission.SECRET_READ, 30, "Read secret");
        req1.setCreatedAt(Instant.now().minusSeconds(100));
        JitAccessRequest req2 = new JitAccessRequest(workspaceId, userId, null, envId, null, AccessPermission.SECRET_READ, 30, "Read secret 2");
        req2.setCreatedAt(Instant.now().minusSeconds(200));
        JitAccessRequest req3 = new JitAccessRequest(workspaceId, userId, null, envId, null, AccessPermission.SECRET_READ, 30, "Read secret 3");
        req3.setCreatedAt(Instant.now().minusSeconds(300));

        when(jitRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(req1, req2, req3));

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.SUSPICIOUS_JIT_ACTIVITY);
    }

    @Test
    @DisplayName("RepeatedAuthorizationFailureRule triggers when denial count reaches threshold")
    void repeatedAuthorizationFailureRule_triggers() {
        RepeatedAuthorizationFailureRule rule = new RepeatedAuthorizationFailureRule(eventRepository);

        List<SecurityEvent> denials = List.of(
                createDenialEvent(userId),
                createDenialEvent(userId),
                createDenialEvent(userId),
                createDenialEvent(userId),
                createDenialEvent(userId)
        );

        when(eventRepository.findRecentEvents(eq(workspaceId), any())).thenReturn(denials);

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.REPEATED_AUTHORIZATION_FAILURES);
    }

    @Test
    @DisplayName("UnusualAdminActivityRule triggers on high volume of admin mutations in 24h")
    void unusualAdminActivityRule_triggersOnMutationSpike() {
        UnusualAdminActivityRule rule = new UnusualAdminActivityRule(eventRepository);

        List<SecurityEvent> adminEvents = List.of(
                createAdminEvent(userId, SecurityEventType.MEMBER_ADDED),
                createAdminEvent(userId, SecurityEventType.MEMBER_ROLE_CHANGED),
                createAdminEvent(userId, SecurityEventType.ACCESS_GRANT_CREATED),
                createAdminEvent(userId, SecurityEventType.ACCESS_GRANT_REVOKED),
                createAdminEvent(userId, SecurityEventType.PROJECT_ACCESS_CHANGED),
                createAdminEvent(userId, SecurityEventType.ENVIRONMENT_ACCESS_CHANGED),
                createAdminEvent(userId, SecurityEventType.MEMBER_ROLE_CHANGED),
                createAdminEvent(userId, SecurityEventType.MEMBER_REMOVED)
        );

        when(eventRepository.findRecentEvents(eq(workspaceId), any())).thenReturn(adminEvents);

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.UNUSUAL_ADMIN_ACTIVITY);
    }

    @Test
    @DisplayName("DormantPrivilegedAccessRule triggers when admin/owner has no activity for 30 days")
    void dormantPrivilegedAccessRule_triggers() {
        DormantPrivilegedAccessRule rule = new DormantPrivilegedAccessRule(membershipRepository, eventRepository);

        WorkspaceMembership admin = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN);
        admin.setCreatedAt(Instant.now().minus(40, ChronoUnit.DAYS));

        when(membershipRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(admin));
        when(eventRepository.findByWorkspaceIdAndActorUserIdSince(eq(workspaceId), eq(userId), any())).thenReturn(List.of());

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.DORMANT_PRIVILEGED_ACCESS);
    }

    @Test
    @DisplayName("AccessReviewOverdueRule triggers when open campaign is past due date")
    void accessReviewOverdueRule_triggers() {
        AccessReviewOverdueRule rule = new AccessReviewOverdueRule(campaignRepository);

        AccessReviewCampaign campaign = new AccessReviewCampaign(
                workspaceId, "Overdue Review", "Desc", AccessScope.WORKSPACE,
                null, null, userId, Instant.now().minus(3, ChronoUnit.DAYS)
        );
        campaign.setStatus(CampaignStatus.OPEN);

        when(campaignRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(campaign));

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.ACCESS_REVIEW_OVERDUE);
    }

    @Test
    @DisplayName("UnusedGrantRule triggers when granular grant is inactive for 14 days")
    void unusedGrantRule_triggers() {
        UnusedGrantRule rule = new UnusedGrantRule(accessGrantRepository, eventRepository);

        AccessGrant grant = new AccessGrant(
                workspaceId, userId, AccessScope.WORKSPACE, null, null, null,
                AccessPermission.SECRET_READ, userId
        );
        grant.setCreatedAt(Instant.now().minus(20, ChronoUnit.DAYS));

        when(accessGrantRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(grant));
        when(eventRepository.findByWorkspaceIdAndActorUserIdSince(eq(workspaceId), eq(userId), any())).thenReturn(List.of());

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.UNUSED_GRANULAR_GRANT);
    }

    @Test
    @DisplayName("AccessConcentrationRule triggers when workspace has only 1 owner and 0 admins with >= 3 members")
    void accessConcentrationRule_triggers() {
        AccessConcentrationRule rule = new AccessConcentrationRule(membershipRepository);

        WorkspaceMembership owner = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
        WorkspaceMembership dev1 = new WorkspaceMembership(workspaceId, UUID.randomUUID(), WorkspaceRole.DEVELOPER);
        WorkspaceMembership dev2 = new WorkspaceMembership(workspaceId, UUID.randomUUID(), WorkspaceRole.VIEWER);

        when(membershipRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(owner, dev1, dev2));

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.ACCESS_CONCENTRATION);
    }

    @Test
    @DisplayName("AuthenticationAnomalyRule triggers on account lockouts or repeated login failures")
    void authenticationAnomalyRule_triggers() {
        AuthenticationAnomalyRule rule = new AuthenticationAnomalyRule(eventRepository);

        SecurityEvent fail1 = new SecurityEvent(workspaceId, null, null, userId, SecurityEventType.AUTH_LOGIN_FAILURE, SecurityEventSeverity.MEDIUM, SecurityEventOutcome.DENIED, "AUTH", null, null, null, "{}");
        SecurityEvent fail2 = new SecurityEvent(workspaceId, null, null, userId, SecurityEventType.AUTH_LOGIN_FAILURE, SecurityEventSeverity.MEDIUM, SecurityEventOutcome.DENIED, "AUTH", null, null, null, "{}");
        SecurityEvent lockout = new SecurityEvent(workspaceId, null, null, userId, SecurityEventType.AUTH_ACCOUNT_LOCKED, SecurityEventSeverity.HIGH, SecurityEventOutcome.DENIED, "AUTH", null, null, null, "{}");

        when(eventRepository.findRecentEvents(eq(workspaceId), any())).thenReturn(List.of(fail1, fail2, lockout));

        List<SecurityFindingDraft> findings = rule.evaluate(workspace, Instant.now());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).category()).isEqualTo(FindingCategory.AUTHENTICATION_ANOMALY);
        assertThat(findings.get(0).severity()).isEqualTo(FindingSeverity.HIGH);
    }

    private SecurityEvent createDenialEvent(UUID actorUserId) {
        return new SecurityEvent(
                workspaceId, null, null, actorUserId,
                SecurityEventType.AUTHORIZATION_DENIED, SecurityEventSeverity.MEDIUM,
                SecurityEventOutcome.DENIED, "SECURITY", null, null, null, "{}"
        );
    }

    private SecurityEvent createAdminEvent(UUID actorUserId, SecurityEventType type) {
        return new SecurityEvent(
                workspaceId, null, null, actorUserId,
                type, SecurityEventSeverity.INFO,
                SecurityEventOutcome.SUCCESS, "SECURITY", null, null, null, "{}"
        );
    }
}
