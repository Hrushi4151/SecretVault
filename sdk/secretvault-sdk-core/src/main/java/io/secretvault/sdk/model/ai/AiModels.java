package io.secretvault.sdk.model.ai;

import java.util.List;
import java.util.UUID;

/**
 * SDK Domain models for Phase 15 AI Intelligence Copilot & DevSecOps Automation.
 */
public final class AiModels {

    private AiModels() {}

    public record AiInquiryRequest(
            String prompt,
            String intent,
            String targetType,
            String targetId
    ) {}

    public record AiInquiryResponse(
            UUID id,
            String prompt,
            String responseContent,
            String intent,
            String modelUsed,
            Double confidenceScore,
            Long latencyMs,
            String advisoryWarning
    ) {}

    public record AiRcaRequest(
            String targetType,
            String targetId,
            String failureLogs
    ) {}

    public record AiRcaResponse(
            UUID id,
            String primaryRootCause,
            String executiveSummary,
            String modelUsed,
            Double confidenceScore,
            List<String> recommendedActions
    ) {}

    public record AiPostureForecast(
            Integer currentScore,
            Integer projected7Days,
            Integer projected14Days,
            String driftVelocity,
            List<String> riskVectors,
            List<String> recommendations
    ) {}

    public record AiPlanInfo(
            UUID id,
            String title,
            String description,
            String status,
            String riskLevel,
            Double confidenceScore
    ) {}
}
