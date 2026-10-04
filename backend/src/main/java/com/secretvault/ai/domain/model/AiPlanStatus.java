package com.secretvault.ai.domain.model;

/**
 * Lifecycle state for reviewable AI remediation plans.
 */
public enum AiPlanStatus {
    PENDING_APPROVAL,
    APPROVED,
    EXECUTED,
    REJECTED,
    EXPIRED
}
