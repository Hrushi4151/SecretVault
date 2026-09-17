package com.secretvault.audit.entity;

/**
 * Enumeration of audited security and lifecycle actions.
 */
public enum AuditAction {
    SECRET_CREATED,
    SECRET_METADATA_UPDATED,
    SECRET_VALUE_UPDATED,
    SECRET_REVEALED,
    SECRET_DELETED,
    SECRET_DISABLED,
    SECRET_ENABLED,
    WORKSPACE_SETTINGS_UPDATED,
    ENVIRONMENT_ACCESS_GRANTED,
    ENVIRONMENT_ACCESS_REVOKED
}
