package io.secretvault.sdk.api;

import io.secretvault.sdk.model.ai.AiModels.*;

import java.util.List;
import java.util.UUID;

/**
 * High-level SDK client API for interacting with SecretVault AI Intelligence Copilot,
 * deployment root-cause analysis, and predictive posture forecasting.
 */
public interface AiDiagnosticsApi {

    AiInquiryResponse inquire(String prompt);

    AiInquiryResponse inquire(AiInquiryRequest request);

    AiRcaResponse runRca(String targetType, String targetId, String failureLogs);

    AiPostureForecast getPostureForecast();

    List<AiPlanInfo> listPlans();

    AiPlanInfo getPlan(UUID planId);

    AiPlanInfo generatePlan(String goal);

    AiPlanInfo approvePlan(UUID planId);

    AiPlanInfo rejectPlan(UUID planId, String reason);

    AiPlanInfo executePlan(UUID planId);
}
