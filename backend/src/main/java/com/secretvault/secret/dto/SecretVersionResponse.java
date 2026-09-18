package com.secretvault.secret.dto;

import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Metadata response representing a historical secret version.
 * Contains cryptographic metadata without revealing plaintext.
 */
public record SecretVersionResponse(
        UUID id,
        UUID secretId,
        Integer versionNumber,
        VersionType versionType,
        String keyReference,
        UUID createdBy,
        Instant createdAt,
        String reason,
        UUID sourceVersionId,
        UUID sourceSecretId,
        UUID sourceEnvironmentId,
        UUID branchId,
        List<String> tags,
        boolean isCurrent
) {
    public static SecretVersionResponse fromEntity(SecretVersion version) {
        return fromEntity(version, Collections.emptyList(), false);
    }

    public static SecretVersionResponse fromEntity(SecretVersion version, List<String> tags, boolean isCurrent) {
        return new SecretVersionResponse(
                version.getId(),
                version.getSecretId(),
                version.getVersionNumber(),
                version.getVersionType(),
                version.getKeyReference(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                version.getReason(),
                version.getSourceVersionId(),
                version.getSourceSecretId(),
                version.getSourceEnvironmentId(),
                version.getBranchId(),
                tags != null ? tags : Collections.emptyList(),
                isCurrent
        );
    }
}
