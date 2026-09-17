package com.secretvault.secret.dto;

import com.secretvault.secret.entity.SecretVersion;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata response representing a historical secret version.
 * Contains cryptographic metadata without revealing plaintext.
 */
public record SecretVersionResponse(
        UUID id,
        UUID secretId,
        Integer versionNumber,
        String keyReference,
        UUID createdBy,
        Instant createdAt,
        String reason
) {
    public static SecretVersionResponse fromEntity(SecretVersion version) {
        return new SecretVersionResponse(
                version.getId(),
                version.getSecretId(),
                version.getVersionNumber(),
                version.getKeyReference(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                version.getReason()
        );
    }
}
