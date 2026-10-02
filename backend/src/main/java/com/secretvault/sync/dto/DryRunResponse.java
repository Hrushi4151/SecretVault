package com.secretvault.sync.dto;

import com.secretvault.sync.model.ReconciliationPolicy;
import com.secretvault.sync.model.SyncScope;

import java.util.List;
import java.util.UUID;

/**
 * Detailed report returned by dry-run simulation.
 * Lists planned operations, detected drift, blocked/unsupported operations, and warnings.
 * Never modifies remote state or stores plaintext values.
 */
public record DryRunResponse(
        UUID jobId,
        UUID workspaceId,
        SyncScope scope,
        UUID scopeResourceId,
        ReconciliationPolicy reconciliationPolicy,
        int totalOperations,
        int createCount,
        int updateCount,
        int deleteCount,
        int noOpCount,
        int blockedCount,
        int driftCount,
        List<SyncOperationResponse> plannedOperations,
        List<DriftRecordResponse> detectedDrifts,
        List<String> warnings
) {}
