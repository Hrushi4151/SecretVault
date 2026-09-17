package com.secretvault.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new secret.
 */
public record CreateSecretRequest(
        @NotBlank(message = "Secret name is required")
        @Size(min = 1, max = 255, message = "Secret name must be between 1 and 255 characters")
        @Pattern(
                regexp = "^[A-Z0-9][A-Z0-9_.-]*$",
                message = "Secret name must start with an uppercase letter or number, and contain only uppercase letters, numbers, underscores, dots, or dashes"
        )
        String name,

        @NotNull(message = "Secret value must not be null")
        @Size(max = 65536, message = "Secret value must not exceed 64KB")
        String value,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description
) {
}
