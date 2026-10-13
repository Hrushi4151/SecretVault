package io.secretvault.sdk.model;

import java.util.List;
import java.util.UUID;

public record PromotionPreviewResult(
        UUID sourceEnvironmentId,
        String sourceEnvironmentName,
        UUID destinationEnvironmentId,
        String destinationEnvironmentName,
        boolean isDestinationProtected,
        int totalCandidates,
        int addedCount,
        int modifiedCount,
        int unchangedCount,
        int conflictCount,
        List<CandidateItem> items
) {
    public record CandidateItem(
            String secretName,
            String status,
            Integer sourceVersion,
            Integer destinationVersion,
            String message
    ) {}
}
