package com.secretvault.automation.model;

/**
 * Safe, controlled action types executable by automation policies.
 * Never allows arbitrary code, scripts, or unchecked execution.
 */
public enum AutomationActionType {
    NOTIFY,
    CREATE_SECURITY_FINDING,
    CREATE_SECURITY_INCIDENT,
    REQUEST_ACCESS_REVIEW,
    TRIGGER_ROTATION,
    REVOKE_LEASE,
    DISABLE_CONSUMER,
    SUSPEND_MACHINE,
    REVOKE_MACHINE,
    CREATE_WEBHOOK_DELIVERY,
    ADD_AUDIT_EVENT
}
