package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CLI DTOs for Phase 15 AI Intelligence Copilot & DevSecOps Platform.
 */
public final class AiCliDtos {

    private AiCliDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiChatRequestCli(
            String prompt,
            String intent,
            String targetType,
            String targetId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiEvidenceCli(
            String evidenceType,
            String source,
            String description,
            String timestamp
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiChatResponseCli(
            UUID id,
            String prompt,
            String responseContent,
            String intent,
            String modelUsed,
            Double confidenceScore,
            Long latencyMs,
            String advisoryWarning,
            List<AiEvidenceCli> sanitizedTelemetryEvidence,
            Instant createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiRcaRequestCli(
            String targetType,
            String targetId,
            String errorContext,
            String failureLogs
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiRcaReportCli(
            UUID id,
            UUID workspaceId,
            String targetType,
            String targetId,
            String primaryRootCause,
            String executiveSummary,
            String modelUsed,
            Double confidenceScore,
            List<String> recommendedActions,
            List<String> evidencePointers,
            Instant createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiPostureForecastCli(
            UUID workspaceId,
            Integer currentPostureScore,
            Integer projectedScore7Days,
            Integer projectedScore14Days,
            String driftVelocity,
            List<String> topRiskVectors,
            List<String> proactiveRecommendations,
            Instant generatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemediationStepCli(
            Integer stepNumber,
            String actionType,
            String targetEntity,
            String description,
            String expectedOutcome
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlastRadiusImpactCli(
            Integer affectedSecretsCount,
            List<String> affectedServices,
            Integer downtimeEstimatedSeconds,
            Boolean requiresStepUpMfa,
            String rollbackComplexity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiRemediationPlanCli(
            UUID id,
            UUID workspaceId,
            String title,
            String description,
            String status,
            String riskLevel,
            Double confidenceScore,
            Boolean requiresHumanApproval,
            List<RemediationStepCli> steps,
            BlastRadiusImpactCli blastRadius,
            Instant createdAt,
            Instant expiresAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiPlanGenerateRequestCli(
            UUID findingId,
            UUID failureLogId,
            String customGoal
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiTokenBudgetCli(
            UUID workspaceId,
            Long dailyTokenQuota,
            Long dailyTokensUsed,
            Integer requestsTodayCount,
            Instant resetTimeUtc
    ) {}
}
