package com.secretvault.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SecretVersionTagRequest(
        @NotBlank(message = "Tag name is required")
        @Size(min = 1, max = 64, message = "Tag name must be between 1 and 64 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Tag name must contain only alphanumeric characters, '_', '.', or '-'")
        String name
) {
}
