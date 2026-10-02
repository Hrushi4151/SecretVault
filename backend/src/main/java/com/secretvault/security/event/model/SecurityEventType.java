package com.secretvault.security.event.model;

/**
 * Standardized security, lifecycle, and access governance event types.
 */
public enum SecurityEventType {
    // Authentication & Identity
    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAILURE,
    AUTH_LOGOUT,
    AUTH_REFRESH,
    AUTH_ACCOUNT_LOCKED,

    // Workspace & Membership Governance
    WORKSPACE_CREATED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    MEMBER_ROLE_CHANGED,

    // Scoped Access
    PROJECT_ACCESS_CHANGED,
    ENVIRONMENT_ACCESS_CHANGED,

    // Granular Access Grants
    ACCESS_GRANT_CREATED,
    ACCESS_GRANT_REVOKED,

    // Just-In-Time Temporary Elevation
    JIT_REQUESTED,
    JIT_APPROVED,
    JIT_REJECTED,
    JIT_EXPIRED,
    JIT_REVOKED,
    JIT_CANCELLED,

    // Access Reviews & Certification
    ACCESS_REVIEW_CREATED,
    ACCESS_REVIEW_ITEM_CERTIFIED,
    ACCESS_REVIEW_ITEM_REVOKED,
    ACCESS_REVIEW_COMPLETED,

    // Core Secret Lifecycle
    SECRET_CREATED,
    SECRET_UPDATED,
    SECRET_DELETED,
    SECRET_REVEALED,
    SECRET_HISTORICAL_REVEALED,
    SECRET_ROLLBACK,
    SECRET_BRANCH,
    SECRET_PROMOTION,

    // Authorization & Policy
    AUTHORIZATION_DENIED,

    // Security Intelligence Findings
    SECURITY_FINDING_CREATED,
    SECURITY_FINDING_RESOLVED,
    SECURITY_ANALYSIS_EXECUTED,

    // Phase 7 Provider Integrations
    PROVIDER_INTEGRATION_CREATED,
    PROVIDER_INTEGRATION_DISABLED,
    PROVIDER_CREDENTIAL_VALIDATION_FAILURE,
    PROVIDER_MAPPING_CHANGED,
    PROVIDER_SECRET_PUSHED
}
