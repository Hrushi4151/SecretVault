package com.secretvault.sync.model;

/**
 * Execution status lifecycle for synchronization jobs.
 */
public enum SyncJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELLED
}
