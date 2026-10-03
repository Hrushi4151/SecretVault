package com.secretvault.access.privileged.model;

/**
 * State machine statuses for privileged access requests.
 * Explicit legal transitions:
 * PENDING -> APPROVED, REJECTED, CANCELLED, EXPIRED
 * APPROVED -> EXECUTED, REVOKED, EXPIRED
 */
public enum PrivilegedRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    EXECUTED,
    REVOKED
}
