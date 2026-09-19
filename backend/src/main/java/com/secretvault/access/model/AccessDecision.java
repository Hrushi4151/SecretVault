package com.secretvault.access.model;

import java.util.Objects;

/**
 * Immutable evaluation result produced by the authorization decision engine.
 * Encapsulates the binary decision (ALLOW/DENY) along with source attribution and rationale.
 */
public record AccessDecision(
        boolean allowed,
        AccessPermission permission,
        AccessScope scope,
        AccessSourceType sourceType,
        String sourceReference,
        String reason,
        String deniedReason
) {

    public static AccessDecision allow(
            AccessPermission permission,
            AccessScope scope,
            AccessSourceType sourceType,
            String sourceReference,
            String reason
    ) {
        return new AccessDecision(
                true,
                permission,
                scope,
                sourceType,
                sourceReference,
                reason,
                null
        );
    }

    public static AccessDecision deny(
            AccessPermission permission,
            String deniedReason
    ) {
        return new AccessDecision(
                false,
                permission,
                null,
                null,
                null,
                null,
                Objects.requireNonNullElse(deniedReason, "Access denied by default policy")
        );
    }

    public static AccessDecision deny(
            AccessPermission permission,
            AccessScope scope,
            String deniedReason
    ) {
        return new AccessDecision(
                false,
                permission,
                scope,
                null,
                null,
                null,
                Objects.requireNonNullElse(deniedReason, "Access denied by default policy")
        );
    }
}
