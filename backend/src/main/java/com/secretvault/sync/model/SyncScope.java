package com.secretvault.sync.model;

/**
 * Granular scope boundary for synchronization operations and drift detection scans.
 */
public enum SyncScope {
    WORKSPACE,
    PROJECT,
    ENVIRONMENT,
    MAPPING,
    SECRET
}
