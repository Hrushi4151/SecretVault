package com.secretvault.sync.dto;

import com.secretvault.sync.model.ReconciliationPolicy;
import com.secretvault.sync.model.SyncScope;

import java.util.UUID;

/**
 * Request payload to initiate a sync dry-run or live synchronization execution.
 */
public record SyncExecutionRequest(
        SyncScope scope,
        UUID scopeResourceId,
        ReconciliationPolicy reconciliationPolicy,
        Boolean asyncExecution
) {
    public SyncScope effectiveScope() {
        return scope != null ? scope : SyncScope.WORKSPACE;
    }

    public ReconciliationPolicy effectivePolicy() {
        return reconciliationPolicy != null ? reconciliationPolicy : ReconciliationPolicy.SAFE_RECONCILIATION;
    }
}
