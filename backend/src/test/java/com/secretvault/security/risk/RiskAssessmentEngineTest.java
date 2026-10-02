package com.secretvault.security.risk;

import com.secretvault.access.model.AccessScope;
import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.repository.AccessReviewCampaignRepository;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.security.risk.model.RiskLevel;
import com.secretvault.security.risk.service.RiskAssessmentEngine;
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
class RiskAssessmentEngineTest {

    @Mock
    private SecurityFindingRepository findingRepository;

    @Mock
    private AccessReviewCampaignRepository campaignRepository;

    @Mock
    private SecurityEventRepository eventRepository;

    private RiskAssessmentEngine riskEngine;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        riskEngine = new RiskAssessmentEngine(findingRepository, campaignRepository, eventRepository);
    }

    @Test
    @DisplayName("Zero risk baseline when no findings or anomalies exist")
    void computeRisk_zeroBaseline() {
        when(findingRepository.findByWorkspaceIdAndStatusIn(eq(workspaceId), any())).thenReturn(List.of());
        when(campaignRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());
        when(eventRepository.countAuthorizationDenialsSince(eq(workspaceId), any())).thenReturn(0L);

        RiskAssessmentEngine.RiskAssessmentResult result = riskEngine.computeRisk(workspaceId);

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.level()).isEqualTo(RiskLevel.LOW);
        assertThat(result.factors()).isEmpty();
    }

    @Test
    @DisplayName("Elevated / High score computed from multiple finding severities and overdue campaigns")
    void computeRisk_accumulatesSeverityAndOverdueFactors() {
        SecurityFinding crit1 = createFinding(FindingSeverity.CRITICAL);
        SecurityFinding high1 = createFinding(FindingSeverity.HIGH);
        SecurityFinding med1 = createFinding(FindingSeverity.MEDIUM);

        when(findingRepository.findByWorkspaceIdAndStatusIn(eq(workspaceId), any()))
                .thenReturn(List.of(crit1, high1, med1));

        AccessReviewCampaign overdue = new AccessReviewCampaign(
                workspaceId, "Q1 Review", "Review", AccessScope.WORKSPACE,
                null, null, userId, Instant.now().minus(5, ChronoUnit.DAYS)
        );
        overdue.setStatus(CampaignStatus.OPEN);

        when(campaignRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(overdue));
        when(eventRepository.countAuthorizationDenialsSince(eq(workspaceId), any())).thenReturn(15L);

        // crit: 25, high: 15, med: 8, overdue: 15, denials >= 10: 10 => 73 (HIGH)
        RiskAssessmentEngine.RiskAssessmentResult result = riskEngine.computeRisk(workspaceId);

        assertThat(result.score()).isEqualTo(73);
        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.factors()).hasSize(5);
    }

    @Test
    @DisplayName("Score is strictly capped at 100 when accumulated score exceeds 100")
    void computeRisk_clampedTo100() {
        SecurityFinding crit1 = createFinding(FindingSeverity.CRITICAL);
        SecurityFinding crit2 = createFinding(FindingSeverity.CRITICAL);
        SecurityFinding crit3 = createFinding(FindingSeverity.CRITICAL);
        SecurityFinding high1 = createFinding(FindingSeverity.HIGH);
        SecurityFinding high2 = createFinding(FindingSeverity.HIGH);
        SecurityFinding med1 = createFinding(FindingSeverity.MEDIUM);
        SecurityFinding med2 = createFinding(FindingSeverity.MEDIUM);
        SecurityFinding med3 = createFinding(FindingSeverity.MEDIUM);

        when(findingRepository.findByWorkspaceIdAndStatusIn(eq(workspaceId), any()))
                .thenReturn(List.of(crit1, crit2, crit3, high1, high2, med1, med2, med3));

        AccessReviewCampaign overdue = new AccessReviewCampaign(
                workspaceId, "Q1 Review", "Review", AccessScope.WORKSPACE,
                null, null, userId, Instant.now().minus(5, ChronoUnit.DAYS)
        );
        overdue.setStatus(CampaignStatus.IN_PROGRESS);

        when(campaignRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(overdue));
        when(eventRepository.countAuthorizationDenialsSince(eq(workspaceId), any())).thenReturn(50L);

        RiskAssessmentEngine.RiskAssessmentResult result = riskEngine.computeRisk(workspaceId);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.level()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    @DisplayName("RiskLevel.fromScore correctly partitions severity bands")
    void riskLevel_thresholds() {
        assertThat(RiskLevel.fromScore(-5)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(0)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(19)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(20)).isEqualTo(RiskLevel.MODERATE);
        assertThat(RiskLevel.fromScore(39)).isEqualTo(RiskLevel.MODERATE);
        assertThat(RiskLevel.fromScore(40)).isEqualTo(RiskLevel.ELEVATED);
        assertThat(RiskLevel.fromScore(59)).isEqualTo(RiskLevel.ELEVATED);
        assertThat(RiskLevel.fromScore(60)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.fromScore(79)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.fromScore(80)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(RiskLevel.fromScore(100)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(RiskLevel.fromScore(150)).isEqualTo(RiskLevel.CRITICAL);
    }

    private SecurityFinding createFinding(FindingSeverity severity) {
        return new SecurityFinding(
                workspaceId, UUID.randomUUID(), null,
                FindingCategory.EXCESSIVE_PRIVILEGE,
                severity,
                FindingConfidence.HIGH,
                "Sample Finding",
                "Description",
                "Remediation",
                "{}",
                UUID.randomUUID().toString()
        );
    }
}
