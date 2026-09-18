package com.secretvault.secret.dto;

import java.util.List;
import java.util.UUID;

public record PromotionPreviewResponse(
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
        List<PromotionCandidateItem> items
) {
    public record PromotionCandidateItem(
            String secretName,
            String status, // "ADDED", "MODIFIED", "UNCHANGED", "CONFLICT_STALE_DESTINATION", "BLOCKED_DISABLED"
            Integer sourceVersion,
            Integer destinationVersion,
            String message
    ) {
    }
}
