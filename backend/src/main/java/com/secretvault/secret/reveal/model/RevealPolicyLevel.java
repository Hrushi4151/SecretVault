package com.secretvault.secret.reveal.model;

/**
 * Categorization of secret reveal security rigor.
 */
public enum RevealPolicyLevel {
    /**
     * Standard development environment baseline.
     */
    DEFAULT,

    /**
     * Staging / pre-production environment with elevated auditing and optional step-up.
     */
    SENSITIVE,

    /**
     * Protected environment requiring mandatory step-up, justification reason, and clipboard timeouts.
     */
    HIGHLY_SENSITIVE,

    /**
     * Mission-critical production environment requiring strict step-up, strong authentication (WebAuthn/TOTP),
     * mandatory 20+ char business justification, short ephemeral reveal countdown, and strict rate limits.
     */
    PRODUCTION_CRITICAL
}
