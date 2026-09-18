package com.secretvault.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBranchRequest(
        @NotBlank(message = "Branch name is required")
        @Size(min = 1, max = 255, message = "Branch name must be between 1 and 255 characters")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._/-]*$", message = "Branch name must start with alphanumeric character and contain only alphanumeric, '.', '_', '/', or '-'")
        String name,

        @NotNull(message = "Source version (fromVersion) is required")
        Integer fromVersion,

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        String description
) {
}
