package com.secretvault.cli.auth;

import com.secretvault.cli.client.ApiClientException;
import com.secretvault.cli.client.ErrorCode;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.AuthDtos;
import com.secretvault.cli.config.CliConfig;
import com.secretvault.cli.config.ConfigManager;
import com.secretvault.cli.config.ProfileConfig;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates authentication, session persistence, automatic token renewal, and profile credentials.
 */
public class AuthManager {

    private final ConfigManager configManager;
    private final CredentialStore credentialStore;
    private final AuthenticationProvider authenticationProvider;

    public AuthManager(ConfigManager configManager, CredentialStore credentialStore) {
        this(configManager, credentialStore, new HumanAuthenticationProvider());
    }

    public AuthManager(ConfigManager configManager, CredentialStore credentialStore, AuthenticationProvider authenticationProvider) {
        this.configManager = configManager;
        this.credentialStore = credentialStore;
        this.authenticationProvider = authenticationProvider;
    }

    public CredentialStore getCredentialStore() {
        return credentialStore;
    }

    public AuthDtos.AuthResponse login(String profileName, String serverUrl, String email, char[] password) {
        CliConfig config = configManager.loadConfig();
        ProfileConfig profile = config.getProfile(profileName);
        if (serverUrl != null && !serverUrl.isBlank()) {
            profile.setServer(serverUrl);
        }

        SecretVaultApiClient client = new SecretVaultApiClient(profile.getServer());
        AuthDtos.AuthResponse response = authenticationProvider.authenticate(client, email, password);

        if (response.accessToken() != null) {
            Instant expiresAt = Instant.now().plusSeconds(response.expiresIn() > 0 ? response.expiresIn() : 86400);
            StoredCredentials creds = new StoredCredentials(
                    response.accessToken(),
                    response.refreshToken(),
                    expiresAt,
                    profile.getServer(),
                    email,
                    response.user() != null ? response.user().id() : null
            );
            credentialStore.save(profileName, profile.getServer(), creds);

            // If activeWorkspace returned, bind default workspace if not set
            if (response.activeWorkspace() != null && profile.getWorkspaceId() == null) {
                profile.setWorkspaceId(response.activeWorkspace().id().toString());
                profile.setWorkspaceSlug(response.activeWorkspace().slug());
            }

            configManager.saveConfig(config);
        }

        return response;
    }

    public AuthDtos.OidcTokenResponse loginWithOidc(String profileName, String serverUrl, UUID providerId, String issuer, String oidcToken) {
        CliConfig config = configManager.loadConfig();
        ProfileConfig profile = config.getProfile(profileName);
        if (serverUrl != null && !serverUrl.isBlank()) {
            profile.setServer(serverUrl);
        }

        SecretVaultApiClient client = new SecretVaultApiClient(profile.getServer());
        AuthDtos.OidcTokenResponse response = client.exchangeOidcToken(new AuthDtos.OidcTokenExchangeRequest(providerId, issuer, oidcToken));

        if (response.accessToken() != null) {
            Instant expiresAt = Instant.now().plusSeconds(response.expiresIn() > 0 ? response.expiresIn() : 600);
            StoredCredentials creds = new StoredCredentials(
                    response.accessToken(),
                    null,
                    expiresAt,
                    profile.getServer(),
                    response.machineIdentity() != null ? response.machineIdentity().name() : "machine",
                    response.machineIdentity() != null ? response.machineIdentity().id() : null
            );
            credentialStore.save(profileName, profile.getServer(), creds);

            if (response.machineIdentity() != null && response.machineIdentity().workspaceId() != null) {
                profile.setWorkspaceId(response.machineIdentity().workspaceId().toString());
            }

            configManager.saveConfig(config);
        }

        return response;
    }

    public void logout(String profileName, String serverUrl) {
        CliConfig config = configManager.loadConfig();
        ProfileConfig profile = config.getProfile(profileName);
        String server = (serverUrl != null && !serverUrl.isBlank()) ? serverUrl : profile.getServer();

        Optional<StoredCredentials> credsOpt = credentialStore.load(profileName, server);
        if (credsOpt.isPresent()) {
            SecretVaultApiClient client = new SecretVaultApiClient(server);
            client.setAccessToken(credsOpt.get().accessToken());
            client.logout();
            credentialStore.delete(profileName, server);
        }
    }

    public void logoutAll() {
        credentialStore.clearAll();
    }

    public Optional<StoredCredentials> getCredentials(String profileName, String serverUrl) {
        return credentialStore.load(profileName, serverUrl);
    }

    public SecretVaultApiClient createAuthenticatedClient(String profileName, String serverUrl) {
        CliConfig config = configManager.loadConfig();
        ProfileConfig profile = config.getProfile(profileName);
        String server = (serverUrl != null && !serverUrl.isBlank()) ? serverUrl : profile.getServer();

        SecretVaultApiClient client = new SecretVaultApiClient(server);
        Optional<StoredCredentials> credsOpt = credentialStore.load(profileName, server);

        if (credsOpt.isEmpty()) {
            throw new ApiClientException(ErrorCode.AUTHENTICATION_FAILED, 401,
                    "Not authenticated. Please run 'secretvault auth login' first.", null);
        }

        StoredCredentials creds = credsOpt.get();
        client.setAccessToken(creds.accessToken());

        // Configure auto-refresh handler
        client.setTokenRefreshHandler(() -> {
            try {
                if (creds.refreshToken() == null || creds.refreshToken().isBlank()) {
                    return false;
                }
                AuthDtos.AuthResponse refreshResp = client.refreshToken(creds.refreshToken());
                if (refreshResp != null && refreshResp.accessToken() != null) {
                    Instant expiresAt = Instant.now().plusSeconds(refreshResp.expiresIn() > 0 ? refreshResp.expiresIn() : 86400);
                    StoredCredentials updatedCreds = new StoredCredentials(
                            refreshResp.accessToken(),
                            refreshResp.refreshToken() != null ? refreshResp.refreshToken() : creds.refreshToken(),
                            expiresAt,
                            server,
                            creds.userEmail(),
                            creds.userId()
                    );
                    credentialStore.save(profileName, server, updatedCreds);
                    client.setAccessToken(updatedCreds.accessToken());
                    return true;
                }
            } catch (Exception e) {
                // Refresh failed (revoked or expired) -> clear credentials
                credentialStore.delete(profileName, server);
            }
            return false;
        });

        // Pre-check expiration
        if (creds.isExpired() && creds.refreshToken() != null) {
            try {
                AuthDtos.AuthResponse refreshResp = client.refreshToken(creds.refreshToken());
                if (refreshResp != null && refreshResp.accessToken() != null) {
                    Instant expiresAt = Instant.now().plusSeconds(refreshResp.expiresIn() > 0 ? refreshResp.expiresIn() : 86400);
                    StoredCredentials updatedCreds = new StoredCredentials(
                            refreshResp.accessToken(),
                            refreshResp.refreshToken() != null ? refreshResp.refreshToken() : creds.refreshToken(),
                            expiresAt,
                            server,
                            creds.userEmail(),
                            creds.userId()
                    );
                    credentialStore.save(profileName, server, updatedCreds);
                    client.setAccessToken(updatedCreds.accessToken());
                }
            } catch (Exception e) {
                credentialStore.delete(profileName, server);
                throw new ApiClientException(ErrorCode.TOKEN_REFRESH_FAILED, 401,
                        "Session expired and token refresh failed. Please run 'secretvault auth login'.", null);
            }
        }

        return client;
    }
}
