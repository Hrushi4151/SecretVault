package com.secretvault.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchCommitRequest(
        @NotBlank(message = "Secret value cannot be blank")
        @Size(max = 65536, message = "Secret value must not exceed 64KB")
        String value,

        Integer expectedHeadVersion,

        @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
        String reason
) {
}
