package com.secretvault.sync.dto;

import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.SyncOperationStatus;
import com.secretvault.sync.model.SyncOperationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response representation of an individual secret sync operation (zero secret plaintext).
 */
public record SyncOperationResponse(
        UUID id,
        UUID jobId,
        UUID secretId,
        String secretName,
        UUID mappingId,
        UUID integrationId,
        SyncOperationType operationType,
        SyncOperationStatus status,
        String desiredFingerprint,
        String observedFingerprint,
        String reason,
        String errorCode,
        String errorMessage,
        Instant executedAt,
        Instant createdAt
) {
    public static SyncOperationResponse fromEntity(SyncOperation entity) {
        if (entity == null) return null;
        return new SyncOperationResponse(
                entity.getId(),
                entity.getJobId(),
                entity.getSecretId(),
                entity.getSecretName(),
                entity.getMappingId(),
                entity.getIntegrationId(),
                entity.getOperationType(),
                entity.getStatus(),
                entity.getDesiredFingerprint(),
                entity.getObservedFingerprint(),
                entity.getReason(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getExecutedAt(),
                entity.getCreatedAt()
        );
    }
}
