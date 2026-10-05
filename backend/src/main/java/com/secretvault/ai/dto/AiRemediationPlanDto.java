package com.secretvault.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        int version,
        AiRiskLevel riskLevel,
        double confidenceScore,
        String targetResourceType,
        String targetResourceId,
        List<RemediationStep> remediationSteps,
        String payloadDiff,
        BlastRadiusImpact blastRadius,
        String planFingerprint,
        boolean requiresFourEyes,
        boolean requiresStepUp,
        AiPlanStatus status,
        UUID createdByUserId,
        UUID reviewedByUserId,
        UUID secondReviewedByUserId,
        Instant executedAt,
        Instant expiresAt,
        String executionResult,
        Integer feedbackRating,
        String feedbackComment,
        Instant createdAt
) {
    public AiRemediationPlanDto(
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
        this(
                id,
                rcaReportId,
                planType,
                title,
                description,
                1,
                riskLevel != null ? riskLevel : AiRiskLevel.MEDIUM,
                confidenceScore,
                targetResourceType,
                targetResourceId,
                remediationSteps,
                payloadDiff,
                blastRadius,
                null,
                false,
                false,
                status,
                null,
                null,
                null,
                null,
                expiresAt,
                null,
                feedbackRating,
                feedbackComment,
                createdAt
        );
    }

    @JsonProperty("steps")
    public List<RemediationStep> steps() {
        return remediationSteps;
    }
}
