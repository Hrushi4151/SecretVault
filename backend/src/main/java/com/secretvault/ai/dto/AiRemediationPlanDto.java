package com.secretvault.ai.dto;

import com.secretvault.ai.domain.model.AiPlanStatus;
import com.secretvault.ai.domain.model.AiRiskLevel;
import com.secretvault.ai.domain.model.BlastRadiusImpact;
import com.secretvault.ai.domain.model.RemediationStep;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiRemediationPlanDto(
        UUID id,
        UUID rcaReportId,
        String planType,
        String title,
        String description,
        AiRiskLevel riskLevel,
        double confidenceScore,
        String targetResourceType,
        String targetResourceId,
        List<RemediationStep> remediationSteps,
        String payloadDiff,
        BlastRadiusImpact blastRadius,
        AiPlanStatus status,
        Instant expiresAt,
        Integer feedbackRating,
        String feedbackComment,
        Instant createdAt
) {
}
