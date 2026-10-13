package com.secretvault.ai.domain.model;

/**
 * An individual reviewable step in an AI remediation sequence.
 */
public record RemediationStep(
        int stepNumber,
        String title,
        String description,
        String actionType,
        String targetResource,
        String status
) {
}
