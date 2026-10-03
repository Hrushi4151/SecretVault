package com.secretvault.events.entity;

/**
 * Status lifecycle states for transactional outbox events.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED,
    DEAD_LETTER,
    CANCELLED
}
