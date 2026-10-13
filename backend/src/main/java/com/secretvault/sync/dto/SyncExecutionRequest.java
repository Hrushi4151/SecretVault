package com.secretvault.sync.dto;

import com.secretvault.sync.model.ReconciliationPolicy;
import com.secretvault.sync.model.SyncScope;

import java.util.UUID;

/**
 * Request payload to initiate a sync dry-run or live synchronization execution.
 * Supports binding execution to an approved dry-run plan (planJobId) for stale-plan rejection.
 */
public record SyncExecutionRequest(
        SyncScope scope,
        UUID scopeResourceId,
        ReconciliationPolicy reconciliationPolicy,
        Boolean asyncExecution,
        UUID planJobId
) {
    public SyncExecutionRequest(
            SyncScope scope,
            UUID scopeResourceId,
            ReconciliationPolicy reconciliationPolicy,
            Boolean asyncExecution
    ) {
        this(scope, scopeResourceId, reconciliationPolicy, asyncExecution, null);
    }

    public SyncScope effectiveScope() {
        return scope != null ? scope : SyncScope.WORKSPACE;
    }

    public ReconciliationPolicy effectivePolicy() {
        return reconciliationPolicy != null ? reconciliationPolicy : ReconciliationPolicy.SAFE_RECONCILIATION;
    }
}
