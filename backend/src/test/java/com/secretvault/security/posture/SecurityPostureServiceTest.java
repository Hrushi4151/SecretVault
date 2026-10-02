package com.secretvault.security.posture;

import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.repository.AccessReviewCampaignRepository;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.security.posture.dto.SecurityOverviewResponse;
import com.secretvault.security.posture.dto.SecurityPostureResponse;
import com.secretvault.security.posture.dto.SecurityTimelineEventResponse;
import com.secretvault.security.posture.service.SecurityPostureService;
import com.secretvault.security.risk.model.RiskLevel;
import com.secretvault.security.risk.service.RiskAssessmentEngine;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityPostureServiceTest {

    @Mock
    private SecurityFindingRepository findingRepository;
    @Mock
    private SecurityEventRepository eventRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AccessReviewCampaignRepository campaignRepository;
    @Mock
    private WorkspaceMembershipRepository membershipRepository;
    @Mock
    private AccessGrantRepository grantRepository;
    @Mock
    private RiskAssessmentEngine riskAssessmentEngine;
    @Mock
    private EffectiveAccessService effectiveAccessService;

    private SecurityPostureService postureService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        postureService = new SecurityPostureService(
                findingRepository,
                eventRepository,
                auditLogRepository,
                campaignRepository,
                membershipRepository,
                grantRepository,
                riskAssessmentEngine,
                effectiveAccessService
        );
    }

    @Test
    @DisplayName("getPosture verifies permission and calculates comprehensive workspace posture")
    void getPosture_calculatesMetrics() {
        when(riskAssessmentEngine.computeRisk(workspaceId))
                .thenReturn(new RiskAssessmentEngine.RiskAssessmentResult(45, RiskLevel.ELEVATED, List.of()));

        when(findingRepository.countByWorkspaceIdAndStatusIn(eq(workspaceId), any())).thenReturn(10L);
        when(findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(eq(workspaceId), eq(FindingSeverity.CRITICAL), any())).thenReturn(1L);
        when(findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(eq(workspaceId), eq(FindingSeverity.HIGH), any())).thenReturn(2L);
        when(findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(eq(workspaceId), eq(FindingSeverity.MEDIUM), any())).thenReturn(4L);
        when(findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(eq(workspaceId), eq(FindingSeverity.LOW), any())).thenReturn(3L);
        when(findingRepository.countByWorkspaceIdAndCategoryInAndStatusIn(eq(workspaceId), any(), any())).thenReturn(5L);

        when(eventRepository.countJitActivitySince(eq(workspaceId), any())).thenReturn(2L);
        when(eventRepository.countAuthorizationDenialsSince(eq(workspaceId), any())).thenReturn(4L);
        when(eventRepository.countAdminChangesSince(eq(workspaceId), any())).thenReturn(3L);

        WorkspaceMembership member = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
        when(membershipRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(member));
        when(grantRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());

        SecurityPostureResponse response = postureService.getPosture(workspaceId, userId);

        assertThat(response).isNotNull();
        assertThat(response.overallRiskScore()).isEqualTo(45);
        assertThat(response.overallRiskLevel()).isEqualTo(RiskLevel.ELEVATED);
        assertThat(response.openFindings()).isEqualTo(10L);
        assertThat(response.criticalFindings()).isEqualTo(1L);
        assertThat(response.highFindings()).isEqualTo(2L);
        assertThat(response.privilegedUserCount()).isEqualTo(1L);

        verify(effectiveAccessService).checkPermission(
                eq(workspaceId), eq(null), eq(null), eq(null),
                eq(AccessPermission.SECURITY_VIEW), eq(userId)
        );
    }

    @Test
    @DisplayName("getOverview combines risk summary, top findings, and recent activity")
    void getOverview_aggregatesDashboardData() {
        when(riskAssessmentEngine.computeRisk(workspaceId))
                .thenReturn(new RiskAssessmentEngine.RiskAssessmentResult(25, RiskLevel.MODERATE, List.of()));

        SecurityFinding finding = new SecurityFinding(
                workspaceId, null, null,
                FindingCategory.EXCESSIVE_PRIVILEGE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Excessive Privileges", "Desc", "Remediation", "{}", "fp123"
        );
        when(findingRepository.findByWorkspaceIdAndStatusIn(eq(workspaceId), any())).thenReturn(List.of(finding));
        when(eventRepository.countAuthorizationDenialsSince(eq(workspaceId), any())).thenReturn(0L);
        when(eventRepository.findRecentEvents(eq(workspaceId), any())).thenReturn(List.of());
        when(auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of());

        SecurityOverviewResponse overview = postureService.getOverview(workspaceId, userId);

        assertThat(overview).isNotNull();
        assertThat(overview.overallRiskScore()).isEqualTo(25);
        assertThat(overview.overallRiskLevel()).isEqualTo(RiskLevel.MODERATE);
        assertThat(overview.topRiskFindings()).hasSize(1);
    }

    @Test
    @DisplayName("getTimeline unifies security events and audit logs in reverse chronological order")
    void getTimeline_mergesAndSorts() {
        SecurityEvent event = new SecurityEvent(
                workspaceId, null, null, userId,
                SecurityEventType.AUTH_LOGIN_SUCCESS, SecurityEventSeverity.INFO,
                SecurityEventOutcome.SUCCESS, "AUTH", null, null, null, "{}"
        );

        AuditLog audit = new AuditLog(
                orgId, workspaceId, userId, "USER", AuditAction.WORKSPACE_SETTINGS_UPDATED,
                "WORKSPACE", workspaceId, "req-1", "127.0.0.1", "SUCCESS"
        );

        when(eventRepository.findRecentEvents(eq(workspaceId), any())).thenReturn(List.of(event));
        when(auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(audit));

        List<SecurityTimelineEventResponse> timeline = postureService.getTimeline(workspaceId, userId, 10);

        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).timelineType()).isIn("AUDIT_EVENT", "SECURITY_EVENT");
        assertThat(timeline.get(1).timelineType()).isIn("AUDIT_EVENT", "SECURITY_EVENT");
    }
}
