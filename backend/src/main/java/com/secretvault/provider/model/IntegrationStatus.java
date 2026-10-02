package com.secretvault.provider.model;

/**
 * Lifecycle status of an external platform provider integration.
 */
public enum IntegrationStatus {
    ACTIVE,
    DISABLED,
    ERROR,
    VALIDATING,
    REVOKED
}
