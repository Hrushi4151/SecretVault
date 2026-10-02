package com.secretvault.sync.model;

import com.secretvault.sync.entity.DriftRecord;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable plan constructed by SyncPlanningEngine before any provider modifications are performed.
 */
public record SyncPlan(
        UUID workspaceId,
        SyncScope scope,
        UUID scopeResourceId,
        ReconciliationPolicy policy,
        List<SyncOperationPlan> operations,
        List<DriftRecord> detectedDrifts,
        List<String> warnings
) {
    public int totalOperations() {
        return operations != null ? operations.size() : 0;
    }

    public int countByType(SyncOperationType type) {
        if (operations == null || type == null) return 0;
        return (int) operations.stream().filter(op -> op.operationType() == type).count();
    }
}
