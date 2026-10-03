package com.secretvault.rotation.model;

/**
 * Verification and validation mechanism applied to freshly generated secret values before activation.
 */
public enum ValidationType {
    NONE,
    CONNECTIVITY,
    AUTHENTICATION,
    PROVIDER_API,
    CUSTOM_HTTP,
    DATABASE_CONNECTION,
    APPLICATION_HEALTH
}
