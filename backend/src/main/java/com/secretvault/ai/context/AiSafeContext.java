package com.secretvault.ai.context;

import com.secretvault.ai.domain.model.AiIntentType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Strongly-typed safe context model for AI reasoning.
 * Contains only non-sensitive structural metadata, counts, fingerprints, and sanitized traces.
 * Zero plaintext credentials or cryptographic keys are accessible.
 */
public record AiSafeContext(
        UUID workspaceId,
        AiIntentType intent,
        long openCriticalFindingsCount,
        long openHighFindingsCount,
        long openMediumFindingsCount,
        long totalOpenFindingsCount,
        List<String> topFindingsSummary,
        String targetResourceType,
        String targetResourceId,
        String targetSummary,
        String sanitizedOperationalHint,
        String timestamp,
        boolean zeroPlaintextEnforced
) {
    public AiSafeContext(
            UUID workspaceId,
            AiIntentType intent,
            long openCriticalFindingsCount,
            long openHighFindingsCount,
            long openMediumFindingsCount,
            long totalOpenFindingsCount,
            List<String> topFindingsSummary,
            String targetResourceType,
            String targetResourceId,
            String targetSummary,
            String sanitizedOperationalHint
    ) {
        this(
                workspaceId,
                intent,
                openCriticalFindingsCount,
                openHighFindingsCount,
                openMediumFindingsCount,
                totalOpenFindingsCount,
                topFindingsSummary != null ? topFindingsSummary : List.of(),
                targetResourceType,
                targetResourceId,
                targetSummary,
                sanitizedOperationalHint,
                Instant.now().toString(),
                true
        );
    }
}
