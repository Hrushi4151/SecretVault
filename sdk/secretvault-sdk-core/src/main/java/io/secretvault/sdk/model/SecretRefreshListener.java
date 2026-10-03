package io.secretvault.sdk.model;

/**
 * Functional callback interface for reacting to secret rotations and refreshes without requiring application restarts.
 */
@FunctionalInterface
public interface SecretRefreshListener {

    /**
     * Invoked when a secret is refreshed or rotated.
     *
     * @param event metadata about the change
     * @param newValue the new decrypted secret value (or null if revoked)
     */
    void onSecretRefreshed(SecretChangeEvent event, SecretValue newValue);
}
