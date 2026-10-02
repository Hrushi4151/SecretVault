package com.secretvault.sync.model;

/**
 * Type of action planned or executed for a secret synchronization operation.
 */
public enum SyncOperationType {
    CREATE,
    UPDATE,
    DELETE,
    NO_OP,
    BLOCKED,
    ERROR
}
