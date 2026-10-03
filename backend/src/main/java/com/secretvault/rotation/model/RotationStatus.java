package com.secretvault.rotation.model;

/**
 * 21-state strict lifecycle state machine for secret rotation jobs.
 */
public enum RotationStatus {
    SCHEDULED,
    QUEUED,
    STARTED,
    GENERATING,
    GENERATED,
    VALIDATING,
    VALIDATED,
    STAGING,
    STAGED,
    ACTIVATING,
    ACTIVE,
    GRACE_PERIOD,
    REVOKING,
    COMPLETED,
    VALIDATION_FAILED,
    ACTIVATION_FAILED,
    ROLLBACK_REQUIRED,
    ROLLED_BACK,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == ROLLED_BACK || this == FAILED || this == CANCELLED || this == EXPIRED;
    }

    public boolean isFailure() {
        return this == VALIDATION_FAILED || this == ACTIVATION_FAILED || this == ROLLBACK_REQUIRED || this == ROLLED_BACK || this == FAILED;
    }

    public boolean isInFlight() {
        return !isTerminal();
    }
}
