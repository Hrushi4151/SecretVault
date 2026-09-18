package com.secretvault.secret.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromotionResultResponse(
        UUID sourceEnvironmentId,
        UUID destinationEnvironmentId,
        int totalRequested,
        int promotedCount,
        int skippedUnchangedCount,
        int failedCount,
        List<PromotedSecretItem> promotedSecrets,
        Instant completedAt
) {
    public record PromotedSecretItem(
            String secretName,
            UUID destinationSecretId,
            Integer newVersionNumber,
            String action, // "CREATED", "UPDATED", "SKIPPED_UNCHANGED"
            String status
    ) {
    }
}
