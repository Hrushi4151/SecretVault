package com.secretvault.rotation.provider;

import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.SecretType;

/**
 * Pluggable provider interface for system-specific secret generation, validation, activation, and revocation.
 */
public interface SecretRotator {

    /**
     * Checks whether this rotator supports the given secret type and policy configuration.
     */
    boolean supports(SecretType type, RotationPolicy policy);

    /**
     * Generates a new credential plaintext for this secret.
     */
    String generate(RotationPolicy policy, RotationJob job);

    /**
     * Validates the generated credential against the target system before staging/activation.
     * Throws an exception or returns false if validation fails.
     */
    boolean validate(String newPlaintext, RotationPolicy policy, RotationJob job);

    /**
     * Optional hook to stage the new credential in the target system (e.g. create dual-user in DB).
     */
    default void stage(String newPlaintext, RotationPolicy policy, RotationJob job) {
        // Default no-op
    }

    /**
     * Activates the new credential as primary in the target system.
     */
    default void activate(String newPlaintext, RotationPolicy policy, RotationJob job) {
        // Default no-op
    }

    /**
     * Revokes or deprovisions the previous credential after the grace period expires.
     */
    default void revokePrevious(String oldPlaintext, RotationPolicy policy, RotationJob job) {
        // Default no-op
    }

    /**
     * Rolls back rotation changes in the external system if validation or rollout fails.
     */
    default void rollback(String newPlaintext, String oldPlaintext, RotationPolicy policy, RotationJob job) {
        // Default no-op
    }
}
