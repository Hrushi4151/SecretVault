package com.secretvault.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.secretvault.ai.domain.model.AiRiskLevel;

import java.util.UUID;

public record AiPlanGenerateRequest(
        @JsonAlias({"customGoal", "description"})
        String goal,
        UUID findingId,
        UUID failureLogId,
        String targetResourceType,
        String targetResourceId,
        AiRiskLevel riskLevel,
        Boolean requiresFourEyes,
        Boolean requiresStepUp
) {
    public String getEffectiveGoal() {
        if (goal != null && !goal.isBlank()) {
            return goal.trim();
        }
        return "Remediate identified security finding or posture drift.";
    }
}
