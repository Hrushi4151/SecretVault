package com.secretvault.sync.model;

/**
 * Execution status for an individual sync operation item.
 */
public enum SyncOperationStatus {
    PENDING,
    SUCCESS,
    FAILED,
    SKIPPED,
    BLOCKED
}
