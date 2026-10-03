package com.secretvault.auth.webauthn.model;

/**
 * Supported WebAuthn ceremony types.
 */
public enum WebAuthnCeremonyType {
    REGISTRATION,
    LOGIN,
    MFA,
    STEP_UP
}
