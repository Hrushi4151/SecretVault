package com.secretvault.secret.dto;

import java.util.UUID;

public record BranchComparisonResponse(
        UUID secretId,
        String branchName,
        Integer baseVersionNumber,
        Integer mainVersionNumber,
        Integer branchHeadVersionNumber,
        boolean hasDiverged,
        boolean canAutoMerge,
        String mergeStatus, // "FAST_FORWARD", "CLEAN_MERGE", "CONFLICT", "UP_TO_DATE"
        String details
) {
}
