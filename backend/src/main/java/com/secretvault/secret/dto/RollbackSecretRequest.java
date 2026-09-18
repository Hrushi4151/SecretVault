package com.secretvault.secret.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for rolling back a secret to a historical version.
 * Rolls back as a brand new version (vN+1) with fresh encryption keys.
 */
public record RollbackSecretRequest(
        @NotNull(message = "Target version is required")
        @Min(value = 1, message = "Target version must be at least 1")
        Integer targetVersion,

        Integer expectedCurrentVersion,

        @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
        String reason
) {
}
