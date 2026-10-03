package com.secretvault.auth.session.enums;

/**
 * Authentication mechanism used to establish a user session.
 */
public enum AuthMethod {
    PASSWORD,
    PASSWORD_MFA_TOTP,
    PASSWORD_MFA_RECOVERY,
    PASSWORD_MFA_WEBAUTHN,
    WEBAUTHN_PASSKEY,
    API_TOKEN
}
