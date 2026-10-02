package com.secretvault.cli.auth;

import java.util.Optional;

/**
 * Contract for secure local credential storage.
 */
public interface CredentialStore {

    /**
     * Persists credentials bound to a specific profile and server.
     */
    void save(String profile, String serverUrl, StoredCredentials credentials);

    /**
     * Loads credentials for a specific profile and server.
     */
    Optional<StoredCredentials> load(String profile, String serverUrl);

    /**
     * Deletes stored credentials for a specific profile and server.
     */
    void delete(String profile, String serverUrl);

    /**
     * Clears all stored credentials across all profiles.
     */
    void clearAll();

    /**
     * Returns whether the secure store is operational and available.
     */
    boolean isAvailable();
}
