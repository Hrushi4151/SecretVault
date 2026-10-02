package com.secretvault.sync.dto;

import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.model.ReconciliationPolicy;
import com.secretvault.sync.model.SyncJobStatus;
import com.secretvault.sync.model.SyncScope;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response representation of a SyncJob (zero secret plaintext).
 */
public record SyncJobResponse(
        UUID id,
        UUID workspaceId,
        SyncScope scope,
        UUID scopeResourceId,
        SyncJobStatus status,
        boolean dryRun,
        ReconciliationPolicy reconciliationPolicy,
        UUID requestedBy,
        Instant startedAt,
        Instant completedAt,
        int totalOperations,
        int successfulOperations,
        int failedOperations,
        int blockedOperations,
        int driftCount,
        String errorSummary,
        Instant createdAt,
        Instant updatedAt
) {
    public static SyncJobResponse fromEntity(SyncJob entity) {
        if (entity == null) return null;
        return new SyncJobResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getScope(),
                entity.getScopeResourceId(),
                entity.getStatus(),
                entity.isDryRun(),
                entity.getReconciliationPolicy(),
                entity.getRequestedBy(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getTotalOperations(),
                entity.getSuccessfulOperations(),
                entity.getFailedOperations(),
                entity.getBlockedOperations(),
                entity.getDriftCount(),
                entity.getErrorSummary(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
