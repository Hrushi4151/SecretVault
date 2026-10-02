package com.secretvault.provider.model;

/**
 * Explicit capabilities supported by provider adapters.
 */
public enum ProviderCapability {
    VALIDATE_CONNECTION,
    DISCOVER_PROJECTS,
    DISCOVER_SERVICES,
    DISCOVER_ENVIRONMENTS,
    READ_SECRET_METADATA,
    WRITE_SECRETS,
    DELETE_SECRETS,
    DEPLOYMENT_TRIGGER,
    DRIFT_DETECTION
}
