package com.secretvault.health.model;

public record RiskFactor(
        String code,
        String title,
        String description,
        String severity, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
        String recommendedRemediation
) {}
