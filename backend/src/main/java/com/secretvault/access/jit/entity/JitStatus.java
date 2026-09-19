package com.secretvault.access.jit.entity;

/**
 * Lifecycle state of a Just-In-Time (JIT) temporary access request.
 */
public enum JitStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    REVOKED,
    CANCELLED
}
