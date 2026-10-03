package com.secretvault.automation.entity;

public enum AutomationExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    AWAITING_APPROVAL,
    DENIED,
    SKIPPED,
    LOOP_ABORTED
}
