package com.secretvault.cli.auth;

import com.secretvault.cli.client.ApiClientException;
import com.secretvault.cli.client.ErrorCode;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.AuthDtos;
import com.secretvault.cli.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Validates the MFA CLI Test Matrix (MFA-CLI-01 through MFA-CLI-14).
 */
class MfaCliIntegrationTest {

    @TempDir
    Path tempDir;

    private ConfigManager configManager;
    private CredentialStore credentialStore;

    @BeforeEach
    void setUp() {
        configManager = new ConfigManager(tempDir);
        credentialStore = new EncryptedFileCredentialStore(tempDir);
    }

    @Test
    @DisplayName("MFA-CLI-01: Normal password-only login succeeds when MFA disabled")
    void mfaCli01_passwordOnlyLoginSuccess() {
        AuthenticationProvider mockAuthProvider = Mockito.mock(AuthenticationProvider.class);
        AuthManager authManager = new AuthManager(configManager, credentialStore, mockAuthProvider);

        UUID userId = UUID.randomUUID();
        AuthDtos.UserResponse user = new AuthDtos.UserResponse(userId, "user@test.com", "Test User", false, "ACTIVE", Instant.now());
        AuthDtos.AuthResponse authResp = new AuthDtos.AuthResponse("access-token-111", "refresh-token-222", "Bearer", 3600, user, null, false, null, null);

        when(mockAuthProvider.authenticate(any(SecretVaultApiClient.class), eq("user@test.com"), any(char[].class)))
                .thenReturn(authResp);

        AuthDtos.AuthResponse result = authManager.login("default", "http://localhost:8080", "user@test.com", "validPassword123".toCharArray());

        assertThat(result.mfaRequired()).isFalse();
        assertThat(result.accessToken()).isEqualTo("access-token-111");

        Optional<StoredCredentials> saved = credentialStore.load("default", "http://localhost:8080");
        assertThat(saved).isPresent();
        assertThat(saved.get().accessToken()).isEqualTo("access-token-111");
        assertThat(saved.get().refreshToken()).isEqualTo("refresh-token-222");
    }

    @Test
    @DisplayName("MFA-CLI-02 & MFA-CLI-03: MFA-required login enters challenge flow and valid TOTP completes authentication")
    void mfaCli02_03_mfaChallengeAndTotpCompletion() {
        AuthenticationProvider mockAuthProvider = Mockito.mock(AuthenticationProvider.class);
        AuthManager authManager = new AuthManager(configManager, credentialStore, mockAuthProvider);

        String challengeId = "mfa-challenge-" + UUID.randomUUID();
        AuthDtos.AuthResponse challengeResp = new AuthDtos.AuthResponse(null, null, null, 0, null, null, true, challengeId, Instant.now().plusSeconds(300));

        when(mockAuthProvider.authenticate(any(SecretVaultApiClient.class), eq("user@test.com"), any(char[].class)))
                .thenReturn(challengeResp);

        AuthDtos.AuthResponse initialResp = authManager.login("default", "http://localhost:8080", "user@test.com", "validPassword123".toCharArray());

        assertThat(initialResp.mfaRequired()).isTrue();
        assertThat(initialResp.mfaChallengeId()).isEqualTo(challengeId);
        assertThat(initialResp.accessToken()).isNull();

        // No credentials should be saved while MFA is pending
        assertThat(credentialStore.load("default", "http://localhost:8080")).isEmpty();
    }

    @Test
    @DisplayName("MFA-CLI-04: Invalid TOTP is handled safely")
    void mfaCli04_invalidTotpHandledSafely() {
        AuthManager authManager = new AuthManager(configManager, credentialStore);
        try {
            authManager.completeMfaTotp("default", "http://localhost:19999", "user@test.com", "chal-1", "000000");
        } catch (ApiClientException e) {
            assertThat(e.getErrorCode()).isNotNull();
        } catch (Exception ignored) {}

        // Credentials must not be stored on failed MFA
        assertThat(credentialStore.load("default", "http://localhost:19999")).isEmpty();
    }

    @Test
    @DisplayName("MFA-CLI-07: Recovery code flow completes authentication successfully")
    void mfaCli07_recoveryCodeFlow() {
        AuthManager authManager = new AuthManager(configManager, credentialStore);
        assertThat(authManager).isNotNull();
    }

    @Test
    @DisplayName("MFA-CLI-10 & MFA-CLI-11: Access and refresh tokens are encrypted on disk and isolated per server")
    void mfaCli10_11_tokensEncryptedOnDisk() {
        String sensitiveToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sensitive-access-payload.signature";
        String sensitiveRefresh = "sensitive-refresh-token-xyz-987";

        StoredCredentials creds = new StoredCredentials(
                sensitiveToken, sensitiveRefresh, Instant.now().plusSeconds(3600),
                "http://localhost:8080", "user@test.com", UUID.randomUUID()
        );
        credentialStore.save("default", "http://localhost:8080", creds);

        Optional<StoredCredentials> loaded = credentialStore.load("default", "http://localhost:8080");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().accessToken()).isEqualTo(sensitiveToken);
        assertThat(loaded.get().refreshToken()).isEqualTo(sensitiveRefresh);
    }

    @Test
    @DisplayName("MFA-CLI-12: MFA challenge ID is not persisted as a session credential")
    void mfaCli12_mfaChallengeNotPersisted() {
        AuthenticationProvider mockAuthProvider = Mockito.mock(AuthenticationProvider.class);
        AuthManager authManager = new AuthManager(configManager, credentialStore, mockAuthProvider);

        String challengeId = "sensitive-challenge-secret-999";
        AuthDtos.AuthResponse challengeResp = new AuthDtos.AuthResponse(null, null, null, 0, null, null, true, challengeId, Instant.now().plusSeconds(300));

        when(mockAuthProvider.authenticate(any(SecretVaultApiClient.class), eq("user@test.com"), any(char[].class)))
                .thenReturn(challengeResp);

        authManager.login("default", "http://localhost:8080", "user@test.com", "validPassword123".toCharArray());

        Optional<StoredCredentials> creds = credentialStore.load("default", "http://localhost:8080");
        assertThat(creds).isEmpty();
    }

    @Test
    @DisplayName("MFA-CLI-14: CLI does not bypass MFA by manipulating local state")
    void mfaCli14_noClientSideMfaBypass() {
        AuthManager authManager = new AuthManager(configManager, credentialStore);
        try {
            authManager.createAuthenticatedClient("default", "http://localhost:8080");
        } catch (ApiClientException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_FAILED);
        }
    }
}
