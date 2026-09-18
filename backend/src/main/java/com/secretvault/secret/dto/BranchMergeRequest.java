package com.secretvault.secret.dto;

import jakarta.validation.constraints.Size;

public record BranchMergeRequest(
        Integer expectedMainVersion,
        Integer expectedBranchHeadVersion,

        @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
        String reason
) {
}
