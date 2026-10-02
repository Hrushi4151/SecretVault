package com.secretvault.sync.dto;

import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.model.DriftSeverity;
import com.secretvault.sync.model.DriftStatus;
import com.secretvault.sync.model.DriftType;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response representation of a DriftRecord (zero secret plaintext).
 */
public record DriftRecordResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        UUID integrationId,
        UUID mappingId,
        UUID secretId,
        String secretName,
        String providerSecretIdentifier,
        DriftType driftType,
        DriftSeverity severity,
        DriftStatus status,
        String desiredFingerprint,
        String observedFingerprint,
        String fingerprint,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        int occurrenceCount,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolutionReason,
        String errorCode,
        String detailsJson,
        Instant createdAt,
        Instant updatedAt
) {
    public static DriftRecordResponse fromEntity(DriftRecord entity) {
        if (entity == null) return null;
        return new DriftRecordResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getProjectId(),
                entity.getEnvironmentId(),
                entity.getIntegrationId(),
                entity.getMappingId(),
                entity.getSecretId(),
                entity.getSecretName(),
                entity.getProviderSecretIdentifier(),
                entity.getDriftType(),
                entity.getSeverity(),
                entity.getStatus(),
                entity.getDesiredFingerprint(),
                entity.getObservedFingerprint(),
                entity.getFingerprint(),
                entity.getFirstDetectedAt(),
                entity.getLastDetectedAt(),
                entity.getOccurrenceCount(),
                entity.getResolvedAt(),
                entity.getResolvedBy(),
                entity.getResolutionReason(),
                entity.getErrorCode(),
                entity.getDetailsJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
