package io.secretvault.sdk.resilience;

/**
 * Defines client resilience strategy when the SecretVault backend is unreachable or returning errors.
 */
public enum ResiliencePolicy {
    /**
     * Strict fail-closed (DEFAULT): If SecretVault is unavailable or the secret is expired in cache,
     * fail immediately and reject access. Recommended for production zero-trust environments.
     */
    FAIL_CLOSED,

    /**
     * Graceful fallback with bounded stale cache: If SecretVault is unavailable, allow serving previously
     * cached secret values up to {@code maxStaleDuration}.
     */
    FAIL_OPEN_WITH_CACHE
}
