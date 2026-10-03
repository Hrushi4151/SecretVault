package com.secretvault.rotation.model;

/**
 * Status lifecycle for runtime secret consumption leases.
 */
public enum LeaseStatus {
    ACTIVE,
    RENEWING,
    EXPIRED,
    REVOKED,
    CANCELLED
}
