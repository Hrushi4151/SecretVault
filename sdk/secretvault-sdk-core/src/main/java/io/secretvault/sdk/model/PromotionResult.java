package io.secretvault.sdk.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromotionResult(
        UUID sourceEnvironmentId,
        UUID destinationEnvironmentId,
        int totalRequested,
        int promotedCount,
        int skippedUnchangedCount,
        int failedCount,
        List<PromotedItem> promotedSecrets,
        Instant completedAt
) {
    public record PromotedItem(
            String secretName,
            UUID destinationSecretId,
            Integer newVersionNumber,
            String action,
            String status
    ) {}
}
