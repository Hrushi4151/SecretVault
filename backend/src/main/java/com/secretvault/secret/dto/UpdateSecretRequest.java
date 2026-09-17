package com.secretvault.secret.dto;

import com.secretvault.secret.entity.SecretStatus;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating secret metadata or creating a new version.
 */
public record UpdateSecretRequest(
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        SecretStatus status,

        @Size(max = 65536, message = "Secret value must not exceed 64KB")
        String value,

        @Size(max = 255, message = "Reason must not exceed 255 characters")
        String reason
) {
}
