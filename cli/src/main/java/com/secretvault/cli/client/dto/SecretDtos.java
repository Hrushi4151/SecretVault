package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SecretDtos {
    private SecretDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecretMetadataDto(
            UUID id,
            UUID environmentId,
            String name,
            String description,
            String status,
            Integer currentVersionNumber,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt,
            String maskedValue
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecretRevealDto(
            UUID id,
            UUID environmentId,
            String name,
            Integer versionNumber,
            String value,
            Instant revealedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecretVersionDto(
            UUID id,
            UUID secretId,
            Integer versionNumber,
            String versionType,
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
    ) {}

    public record CreateSecretRequest(
            String name,
            String value,
            String description
    ) {}

    public record UpdateSecretRequest(
            String description,
            String status,
            String value,
            String reason
    ) {}

    public record RollbackSecretRequest(
            Integer targetVersion,
            Integer expectedCurrentVersion,
            String reason
    ) {}

    public record BatchImportRequest(
            List<CreateSecretRequest> secrets,
            boolean overwriteExisting
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchImportResponse(
            int createdCount,
            int updatedCount,
            int skippedCount,
            List<String> createdKeys,
            List<String> updatedKeys,
            List<String> skippedKeys
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageResponse<T>(
            List<T> content,
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {}
}
