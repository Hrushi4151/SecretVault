package com.secretvault.security.posture.dto;

import com.secretvault.security.risk.model.RiskFactorExplanation;
import com.secretvault.security.risk.model.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SecurityPostureResponse(
        UUID workspaceId,
        int overallRiskScore,
        RiskLevel overallRiskLevel,
        long openFindings,
        long criticalFindings,
        long highFindings,
        long mediumFindings,
        long lowFindings,
        long unresolvedAccessFindings,
        long jitActivity24h,
        long authorizationDenials24h,
        long adminChanges24h,
        String accessReviewStatus,
        long privilegedUserCount,
        long unusedGrantCount,
        long dormantPrivilegedAccountCount,
        List<RiskFactorExplanation> riskFactors,
        Instant calculatedAt
) {}
