package com.secretvault.rotation.model;

/**
 * Health and operational status of a registered consumer workload.
 */
public enum ConsumerStatus {
    ACTIVE,
    INACTIVE,
    STALE,
    DISABLED
}
