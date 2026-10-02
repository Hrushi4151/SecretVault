package com.secretvault.cli.auth;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.AuthDtos;

/**
 * Authentication abstraction supporting Human Credentials and future Phase 9 Machine/OIDC tokens.
 */
public interface AuthenticationProvider {

    /**
     * Authenticates with SecretVault backend and returns session tokens and user profile.
     */
    AuthDtos.AuthResponse authenticate(SecretVaultApiClient apiClient, String usernameOrClient, char[] passwordOrSecret);

    /**
     * Authentication method identifier.
     */
    String getAuthType();
}
