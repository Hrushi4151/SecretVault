package com.secretvault.sync.model;

/**
 * Lifecycle states for an individual detected drift record.
 */
public enum DriftStatus {
    OPEN,
    ACKNOWLEDGED,
    SYNC_PENDING,
    RESOLVED,
    IGNORED,
    ERROR
}
