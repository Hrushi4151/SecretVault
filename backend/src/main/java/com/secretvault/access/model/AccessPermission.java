package com.secretvault.access.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/**
 * Canonical enumeration of granular access permissions in SecretVault.
 * Defines the standard machine-readable permission codes across all resource scopes.
 */
public enum AccessPermission {

    SECRET_READ(
            "secret.read",
            "View secret metadata, version history, and tags"
    ),

    SECRET_CREATE(
            "secret.create",
            "Create new secrets in an environment"
    ),

    SECRET_UPDATE(
            "secret.update",
            "Update secret metadata or rotate secret value"
    ),

    SECRET_DELETE(
            "secret.delete",
            "Soft delete secrets"
    ),

    SECRET_REVEAL(
            "secret.reveal",
            "Decrypt and reveal plaintext secret value"
    ),

    SECRET_ROLLBACK(
            "secret.rollback",
            "Rollback a secret to a historical version"
    ),

    SECRET_BRANCH(
            "secret.branch",
            "Create and commit to Development feature branches"
    ),

    ENVIRONMENT_PROMOTE(
            "environment.promote",
            "Promote secrets across environments"
    ),

    ENVIRONMENT_MANAGE(
            "environment.manage",
            "Configure environment settings and protection"
    ),

    ACCESS_MANAGE(
            "access.manage",
            "Grant or revoke standing access permissions"
    ),

    JIT_REQUEST(
            "jit.request",
            "Request temporary elevated access"
    ),

    JIT_APPROVE(
            "jit.approve",
            "Approve or reject JIT access within managed scope"
    ),

    ACCESS_REVIEW_MANAGE(
            "access_review.manage",
            "Create and certify access review campaigns"
    );

    private final String code;
    private final String description;

    AccessPermission(String code, String description) {
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
    public static AccessPermission fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String cleanCode = code.trim().toLowerCase();
        for (AccessPermission perm : values()) {
            if (perm.code.equalsIgnoreCase(cleanCode) || perm.name().equalsIgnoreCase(cleanCode)) {
                return perm;
            }
        }
        throw new IllegalArgumentException("Unknown access permission code: " + code);
    }

    public static Optional<AccessPermission> tryFromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String cleanCode = code.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(p -> p.code.equalsIgnoreCase(cleanCode) || p.name().equalsIgnoreCase(cleanCode))
                .findFirst();
    }
}
