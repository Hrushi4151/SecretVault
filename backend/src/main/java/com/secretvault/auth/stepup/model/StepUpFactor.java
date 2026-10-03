package com.secretvault.auth.stepup.model;

/**
 * Authentication factors supported for Step-Up re-authentication.
 */
public enum StepUpFactor {
    PASSWORD,
    TOTP,
    RECOVERY_CODE
}
