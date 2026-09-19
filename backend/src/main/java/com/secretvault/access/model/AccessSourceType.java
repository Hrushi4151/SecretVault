package com.secretvault.access.model;

/**
 * Identifies the authoritative origin/source of an authorization grant.
 * Used for access explanation and targeted governance reviews.
 */
public enum AccessSourceType {
    WORKSPACE_ROLE,
    PROJECT_ACCESS,
    ENVIRONMENT_ACCESS,
    GRANULAR_GRANT,
    JIT_GRANT
}
