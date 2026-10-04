package com.secretvault.ai.dto;

import java.util.List;

public record AiPostureForecastResponse(
        double currentScore,
        double predictedScore48h,
        double driftVelocityPerHour,
        double remediationTtrMinutes,
        String compromiseProbability,
        List<TopAutonomousFinding> topFindings
) {
    public record TopAutonomousFinding(
            String id,
            String title,
            String severity,
            double confidence,
            String targetResource,
            String description,
            String recommendedAction
    ) {}
}
