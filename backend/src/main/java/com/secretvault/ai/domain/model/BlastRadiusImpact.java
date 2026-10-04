package com.secretvault.ai.domain.model;

import java.util.List;

/**
 * Quantified downstream impact across projects, environments, and consumers.
 */
public record BlastRadiusImpact(
        String targetIdentifier,
        String tier,
        List<String> affectedEnvironments,
        List<String> affectedServices,
        int activeConsumersCount,
        double simulatedFailureProbabilityPercentage,
        String exposureAssessment
) {
}
