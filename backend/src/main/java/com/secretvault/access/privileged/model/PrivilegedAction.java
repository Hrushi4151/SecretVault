package com.secretvault.access.privileged.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/**
 * Centralized enumeration of sensitive and privileged operations in SecretVault.
 * Defines operations subject to elevated governance, quorum approvals, step-up authentication,
 * and break-glass emergency procedures.
 */
public enum PrivilegedAction {
    SECRET_REVEAL("SECRET_REVEAL", "Decrypt and reveal plaintext secret value"),
    SECRET_DELETE("SECRET_DELETE", "Permanently or soft delete secret"),
    SECRET_ROLLBACK("SECRET_ROLLBACK", "Rollback secret to a previous historical version"),
    ENVIRONMENT_PROMOTE("ENVIRONMENT_PROMOTE", "Promote secret versions across environments"),
    ACCESS_GRANT("ACCESS_GRANT", "Grant access permissions or roles"),
    ACCESS_REVOKE("ACCESS_REVOKE", "Revoke access permissions or roles"),
    JIT_APPROVE("JIT_APPROVE", "Approve JIT elevation requests"),
    JIT_REVOKE("JIT_REVOKE", "Revoke active JIT access grants"),
    SESSION_REVOKE_ALL("SESSION_REVOKE_ALL", "Revoke all active user sessions"),
    MFA_DISABLE("MFA_DISABLE", "Disable multi-factor authentication"),
    WEBAUTHN_CREDENTIAL_REVOKE("WEBAUTHN_CREDENTIAL_REVOKE", "Revoke WebAuthn/Passkey credential"),
    ROLE_CHANGE("ROLE_CHANGE", "Modify workspace member roles"),
    MEMBER_REMOVE("MEMBER_REMOVE", "Remove member from workspace"),
    PROJECT_ACCESS_CHANGE("PROJECT_ACCESS_CHANGE", "Modify scoped project access"),
    ENVIRONMENT_ACCESS_CHANGE("ENVIRONMENT_ACCESS_CHANGE", "Modify scoped environment access"),
    PRIVILEGED_POLICY_CHANGE("PRIVILEGED_POLICY_CHANGE", "Create or update privileged access security policies"),
    BREAK_GLASS_REQUEST("BREAK_GLASS_REQUEST", "Emergency elevated access request");

    private final String code;
    private final String description;

    PrivilegedAction(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static PrivilegedAction fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim().toUpperCase();
        for (PrivilegedAction action : values()) {
            if (action.code.equalsIgnoreCase(clean) || action.name().equalsIgnoreCase(clean)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown privileged action: " + value);
    }

    public static Optional<PrivilegedAction> tryFromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String clean = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(a -> a.code.equalsIgnoreCase(clean) || a.name().equalsIgnoreCase(clean))
                .findFirst();
    }
}
