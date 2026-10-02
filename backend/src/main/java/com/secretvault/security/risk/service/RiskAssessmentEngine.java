package com.secretvault.security.risk.service;

import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.repository.AccessReviewCampaignRepository;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.security.risk.model.RiskFactorExplanation;
import com.secretvault.security.risk.model.RiskLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RiskAssessmentEngine {

    private final SecurityFindingRepository findingRepository;
    private final AccessReviewCampaignRepository campaignRepository;
    private final SecurityEventRepository eventRepository;

    private static final Set<FindingStatus> UNRESOLVED_STATUSES = Set.of(
            FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED, FindingStatus.IN_PROGRESS
    );

    public RiskAssessmentEngine(
            SecurityFindingRepository findingRepository,
            AccessReviewCampaignRepository campaignRepository,
            SecurityEventRepository eventRepository
    ) {
        this.findingRepository = findingRepository;
        this.campaignRepository = campaignRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public RiskAssessmentResult computeRisk(UUID workspaceId) {
        List<SecurityFinding> unresolvedFindings = findingRepository.findByWorkspaceIdAndStatusIn(
                workspaceId, UNRESOLVED_STATUSES
        );

        int rawScore = 0;
        List<RiskFactorExplanation> factors = new ArrayList<>();

        // 1. Critical Findings Factor
        List<SecurityFinding> criticals = unresolvedFindings.stream()
                .filter(f -> f.getSeverity() == FindingSeverity.CRITICAL)
                .toList();
        if (!criticals.isEmpty()) {
            int weight = Math.min(50, criticals.size() * 25);
            rawScore += weight;
            factors.add(new RiskFactorExplanation(
                    "critical_findings",
                    "Critical Security Findings",
                    weight,
                    criticals.size() + " unresolved critical severity finding(s) require immediate remediation",
                    criticals.stream().map(f -> f.getId().toString()).toList()
            ));
        }

        // 2. High Severity Findings Factor
        List<SecurityFinding> highs = unresolvedFindings.stream()
                .filter(f -> f.getSeverity() == FindingSeverity.HIGH)
                .toList();
        if (!highs.isEmpty()) {
            int weight = Math.min(30, highs.size() * 15);
            rawScore += weight;
            factors.add(new RiskFactorExplanation(
                    "high_findings",
                    "High Severity Findings",
                    weight,
                    highs.size() + " unresolved high severity finding(s) detected",
                    highs.stream().map(f -> f.getId().toString()).toList()
            ));
        }

        // 3. Medium Severity Findings Factor
        List<SecurityFinding> mediums = unresolvedFindings.stream()
                .filter(f -> f.getSeverity() == FindingSeverity.MEDIUM)
                .toList();
        if (!mediums.isEmpty()) {
            int weight = Math.min(20, mediums.size() * 8);
            rawScore += weight;
            factors.add(new RiskFactorExplanation(
                    "medium_findings",
                    "Medium Severity Findings",
                    weight,
                    mediums.size() + " unresolved medium severity finding(s) present",
                    mediums.stream().map(f -> f.getId().toString()).toList()
            ));
        }

        // 4. Low Severity Findings Factor
        List<SecurityFinding> lows = unresolvedFindings.stream()
                .filter(f -> f.getSeverity() == FindingSeverity.LOW)
                .toList();
        if (!lows.isEmpty()) {
            int weight = Math.min(10, lows.size() * 3);
            rawScore += weight;
            factors.add(new RiskFactorExplanation(
                    "low_findings",
                    "Low Severity Hygiene Warnings",
                    weight,
                    lows.size() + " low severity posture or hygiene finding(s)",
                    lows.stream().map(f -> f.getId().toString()).toList()
            ));
        }

        // 5. Overdue Access Reviews Factor
        Instant now = Instant.now();
        List<AccessReviewCampaign> overdueCampaigns = campaignRepository.findByWorkspaceId(workspaceId).stream()
                .filter(c -> (c.getStatus() == CampaignStatus.OPEN || c.getStatus() == CampaignStatus.IN_PROGRESS) && c.getDueDate().isBefore(now))
                .toList();
        if (!overdueCampaigns.isEmpty()) {
            int weight = 15;
            rawScore += weight;
            factors.add(new RiskFactorExplanation(
                    "overdue_access_reviews",
                    "Delinquent Access Certification",
                    weight,
                    overdueCampaigns.size() + " active access review campaign(s) are overdue for compliance sign-off",
                    overdueCampaigns.stream().map(c -> c.getId().toString()).toList()
            ));
        }

        // 6. Elevated Authorization Denial Velocity Factor
        long denials24h = eventRepository.countAuthorizationDenialsSince(workspaceId, now.minus(Duration.ofHours(24)));
        if (denials24h >= 10) {
            int weight = 10;
            rawScore += weight;
            factors.add(new RiskFactorExplanation(
                    "authorization_denial_surge",
                    "Authorization Denial Velocity",
                    weight,
                    denials24h + " authorization denials observed in the last 24 hours",
                    List.of("denials_24h_count:" + denials24h)
            ));
        }

        // Strictly bound score between 0 and 100
        int finalScore = Math.max(0, Math.min(100, rawScore));
        RiskLevel level = RiskLevel.fromScore(finalScore);

        return new RiskAssessmentResult(finalScore, level, factors);
    }

    public record RiskAssessmentResult(
            int score,
            RiskLevel level,
            List<RiskFactorExplanation> factors
    ) {}
}
