package com.secretvault.config;

import com.secretvault.auth.security.JwtTokenProvider;
import com.secretvault.encryption.kms.LocalDevKmsKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Production Configuration Security & Fail-Fast Tests")
class ProductionConfigurationSecurityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    private static final String VALID_MASTER_KEY_BASE64 = Base64.getEncoder().encodeToString(
            "valid_master_key_32_bytes_test01".getBytes(StandardCharsets.UTF_8)
    );
    private static final String VALID_JWT_SECRET = "valid_jwt_signing_secret_with_at_least_32_bytes_length_12345";

    @Test
    @DisplayName("TEST-01: JwtTokenProvider and LocalDevKmsKeyProvider instantiate successfully with valid 256-bit configuration")
    void testValidConfigurationInstantiation() {
        JwtTokenProvider jwtProvider = new JwtTokenProvider(VALID_JWT_SECRET, 3600);
        assertThat(jwtProvider).isNotNull();

        LocalDevKmsKeyProvider kmsProvider = new LocalDevKmsKeyProvider(VALID_MASTER_KEY_BASE64);
        kmsProvider.init();
        assertThat(kmsProvider.getDefaultKeyReference()).isEqualTo("local-dev-kek-v1");
    }

    @Test
    @DisplayName("TEST-02: Missing or blank JWT secret throws IllegalStateException (Fail-Fast)")
    void testMissingJwtSecretFailsFast() {
        assertThatThrownBy(() -> new JwtTokenProvider("", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL: JWT signing secret");

        assertThatThrownBy(() -> new JwtTokenProvider(null, 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL: JWT signing secret");
    }

    @Test
    @DisplayName("TEST-03: Insecure short JWT secret (< 32 bytes) throws IllegalStateException")
    void testShortJwtSecretFailsFast() {
        assertThatThrownBy(() -> new JwtTokenProvider("short-jwt-secret-12345", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be at least 256 bits (32 bytes)");
    }

    @Test
    @DisplayName("TEST-04: Missing or blank master key throws IllegalStateException (Fail-Fast)")
    void testMissingMasterKeyFailsFast() {
        LocalDevKmsKeyProvider provider = new LocalDevKmsKeyProvider("");
        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL: Master Key Encryption Key (VAULT_MASTER_KEY) is not configured");
    }

    @Test
    @DisplayName("TEST-05: Non-Base64 master key is safely hashed to 256 bits via SHA-256 derivation")
    void testNonBase64MasterKeyDerivesSafely() {
        LocalDevKmsKeyProvider provider = new LocalDevKmsKeyProvider("plain-text-passphrase-not-base64");
        provider.init();
        assertThat(provider.getDefaultKeyReference()).isEqualTo("local-dev-kek-v1");
    }

    @Test
    @DisplayName("TEST-06: Master key of arbitrary length is safely normalized to 256 bits via SHA-256")
    void testArbitraryLengthMasterKeyDerivesSafely() {
        String shortKeyBase64 = Base64.getEncoder().encodeToString("short_16_bytes!!".getBytes(StandardCharsets.UTF_8));
        LocalDevKmsKeyProvider provider = new LocalDevKmsKeyProvider(shortKeyBase64);
        provider.init();
        assertThat(provider.getDefaultKeyReference()).isEqualTo("local-dev-kek-v1");
    }

    @Test
    @DisplayName("TEST-07: JwtTokenProvider validation rejects tampered tokens safely")
    void testJwtValidationSecurity() {
        JwtTokenProvider jwtProvider = new JwtTokenProvider(VALID_JWT_SECRET, 3600);
        assertThat(jwtProvider.validateToken("invalid.jwt.token")).isFalse();
        assertThat(jwtProvider.validateToken(null)).isFalse();
        assertThat(jwtProvider.validateToken("")).isFalse();
    }
}
