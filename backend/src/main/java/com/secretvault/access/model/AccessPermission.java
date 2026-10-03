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
    ),

    SECURITY_VIEW(
            "security.view",
            "View security findings, events, timeline, and posture"
    ),

    SECURITY_MANAGE(
            "security.manage",
            "Acknowledge, assign, resolve security findings, and trigger intelligence analysis"
    ),

    INTEGRATION_VIEW(
            "integration.view",
            "View external provider integrations, capabilities, and resource mappings"
    ),

    INTEGRATION_MANAGE(
            "integration.manage",
            "Create, update, validate, and delete external provider integrations and mappings"
    ),

    INTEGRATION_SYNC(
            "integration.sync",
            "Synchronize and push secrets to external platform providers"
    ),

    SYNC_VIEW(
            "sync.view",
            "View sync status, dry-run reports, and sync jobs"
    ),

    SYNC_DRY_RUN(
            "sync.dry_run",
            "Run dry-run drift simulation and sync planning"
    ),

    SYNC_EXECUTE(
            "sync.execute",
            "Execute live synchronization to external providers"
    ),

    DRIFT_VIEW(
            "drift.view",
            "View detected drift records and comparison metrics"
    ),

    DRIFT_MANAGE(
            "drift.manage",
            "Acknowledge, ignore, and manage drift record status"
    ),

    // Phase 12: Secret Rotation, Leases & Consumers
    SECRET_ROTATION_READ(
            "secret.rotation.read",
            "View rotation policies, jobs, execution history, and impact analysis"
    ),

    SECRET_ROTATION_CREATE(
            "secret.rotation.create",
            "Trigger manual or scheduled secret rotation jobs"
    ),

    SECRET_ROTATION_MANAGE(
            "secret.rotation.manage",
            "Pause, resume, retry, and manage active rotation jobs"
    ),

    SECRET_ROTATION_CANCEL(
            "secret.rotation.cancel",
            "Cancel active in-flight secret rotation jobs"
    ),

    SECRET_ROTATION_ROLLBACK(
            "secret.rotation.rollback",
            "Rollback secret rotation to previous verified version"
    ),

    SECRET_ROTATION_EMERGENCY(
            "secret.rotation.emergency",
            "Trigger emergency rotation and mark secrets as compromised"
    ),

    SECRET_ROTATION_POLICY_MANAGE(
            "secret.rotation.policy.manage",
            "Create, update, and disable secret rotation policies"
    ),

    SECRET_LEASE_READ(
            "secret.lease.read",
            "View active and historical secret leases"
    ),

    SECRET_LEASE_MANAGE(
            "secret.lease.manage",
            "Issue, renew, and revoke runtime secret leases"
    ),

    CONSUMER_MANAGE(
            "consumer.manage",
            "Register, heartbeat, and govern runtime secret consumers"
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
