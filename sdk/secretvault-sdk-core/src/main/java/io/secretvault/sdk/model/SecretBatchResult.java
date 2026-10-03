package io.secretvault.sdk.model;

import io.secretvault.sdk.exception.SecretVaultException;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsulates the results of a multi-secret batch retrieval.
 * Differentiates successful in-memory secret resolutions from granular authorization or retrieval failures.
 */
public record SecretBatchResult(
        Map<String, SecretValue> successes,
        Map<String, SecretVaultException> failures
) {
    public SecretBatchResult {
        successes = successes != null ? Collections.unmodifiableMap(successes) : Collections.emptyMap();
        failures = failures != null ? Collections.unmodifiableMap(failures) : Collections.emptyMap();
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public boolean isCompleteSuccess() {
        return failures.isEmpty();
    }

    public Optional<SecretValue> get(String name) {
        return Optional.ofNullable(successes.get(name));
    }

    public Optional<SecretVaultException> getFailure(String name) {
        return Optional.ofNullable(failures.get(name));
    }
}
