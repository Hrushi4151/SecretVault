package io.secretvault.sdk.auth;

/**
 * Strategy interface for supplying authentication credentials to SecretVault API requests.
 */
public interface CredentialsProvider extends AutoCloseable {

    /**
     * Obtains a valid Bearer token for HTTP Authorization headers.
     * Must return without the 'Bearer ' prefix.
     */
    String getBearerToken();

    /**
     * Indicates if this provider supports proactive or on-demand token re-authentication.
     */
    default boolean supportsRefresh() {
        return false;
    }

    /**
     * Forces an immediate token refresh if supported.
     */
    default void refresh() {}

    @Override
    default void close() {}
}
