package com.secretvault.secret.dto;

import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata response for secret list and detail endpoints.
 * Never exposes plaintext secrets.
 */
public record SecretMetadataResponse(
        UUID id,
        UUID environmentId,
        String name,
        String description,
        SecretStatus status,
        Integer currentVersionNumber,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        String maskedValue
) {
    public static final String MASKED_VALUE_PLACEHOLDER = "••••••••••••••••";

    public static SecretMetadataResponse fromEntity(Secret secret) {
        return new SecretMetadataResponse(
                secret.getId(),
                secret.getEnvironmentId(),
                secret.getName(),
                secret.getDescription(),
                secret.getStatus(),
                secret.getCurrentVersionNumber(),
                secret.getCreatedBy(),
                secret.getCreatedAt(),
                secret.getUpdatedAt(),
                MASKED_VALUE_PLACEHOLDER
        );
    }
}
