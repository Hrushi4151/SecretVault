package com.secretvault.auth.mfa.model;

/**
 * State machine representing authentication and MFA verification stages.
 */
public enum AuthenticationState {
    AUTHENTICATION_NOT_STARTED,
    PASSWORD_VERIFIED,
    MFA_REQUIRED,
    MFA_VERIFIED,
    AUTHENTICATED,
    AUTHENTICATION_FAILED,
    MFA_CHALLENGE_EXPIRED,
    MFA_CHALLENGE_LOCKED
}
