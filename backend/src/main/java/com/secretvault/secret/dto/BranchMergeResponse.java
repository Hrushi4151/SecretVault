package com.secretvault.secret.dto;

import java.util.UUID;

public record BranchMergeResponse(
        UUID secretId,
        UUID branchId,
        String branchName,
        Integer mergeVersionNumber,
        String status, // "MERGED", "CONFLICT"
        String message
) {
}
