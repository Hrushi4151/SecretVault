package com.secretvault.ai.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AiContextSanitizer Security Boundary Tests")
class AiContextSanitizerTest {

    private AiContextSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new AiContextSanitizer();
    }

    @Test
    @DisplayName("Should scrub RSA/EC PEM private keys completely")
    void testScrubPrivateKeys() {
        String raw = """
                Here is the error log:
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA0Y8...fake...private...key...data...
                -----END RSA PRIVATE KEY-----
                deployment crashed after reading key.
                """;

        String sanitized = sanitizer.sanitizeText(raw);
        assertFalse(sanitized.contains("MIIEowIBAAKCAQEA"));
        assertTrue(sanitized.contains("[REDACTED_ASYMMETRIC_PRIVATE_KEY]"));
    }

    @Test
    @DisplayName("Should scrub live Stripe and GitHub tokens")
    void testScrubTokens() {
        String raw = "Failed during sync with Stripe token sk_live_99887766554433221100 and GitHub token ghp_abcdefghijklmnopqrstuvwxyz123456";
        String sanitized = sanitizer.sanitizeText(raw);

        assertFalse(sanitized.contains("sk_live_99887766554433221100"));
        assertFalse(sanitized.contains("ghp_abcdefghijklmnopqrstuvwxyz123456"));
        assertTrue(sanitized.contains("[REDACTED_SECRET_TOKEN]"));
    }

    @Test
    @DisplayName("Should scrub connection string passwords")
    void testScrubUriCredentials() {
        String raw = "Database connection string: postgres://admin_user:SuperSecretPassword123!@db.internal.acme.com:5432/vault_prod";
        String sanitized = sanitizer.sanitizeText(raw);

        assertFalse(sanitized.contains("SuperSecretPassword123!"));
        assertTrue(sanitized.contains("postgres://admin_user:[REDACTED_CREDENTIAL]@db.internal.acme.com:5432/vault_prod"));
    }

    @Test
    @DisplayName("Should replace key-value passwords with deterministic SHA-256 fingerprint prefixes")
    void testScrubKeyValueSecrets() {
        String raw = "config: password=my_db_password_xyz, api_key: 'stripe_key_val_12345'";
        String sanitized = sanitizer.sanitizeText(raw);

        assertFalse(sanitized.contains("my_db_password_xyz"));
        assertFalse(sanitized.contains("stripe_key_val_12345"));
        assertTrue(sanitized.contains("password=[SHA256:"));
        assertTrue(sanitized.contains("api_key=[SHA256:"));
    }

    @Test
    @DisplayName("assertZeroPlaintext throws SecurityException when sensitive patterns are detected")
    void testAssertZeroPlaintextViolations() {
        assertThrows(SecurityException.class, () ->
                sanitizer.assertZeroPlaintext("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----")
        );

        assertThrows(SecurityException.class, () ->
                sanitizer.assertZeroPlaintext("token sk_live_1234567890abcdef1234")
        );

        assertThrows(SecurityException.class, () ->
                sanitizer.assertZeroPlaintext("redis://default:secretpass123@redis:6379")
        );

        // Safe metadata should pass without exception
        assertDoesNotThrow(() ->
                sanitizer.assertZeroPlaintext("target: STRIPE_SECRET_KEY, version: 8, status: SYNCED")
        );
    }

    @Test
    @DisplayName("computeDigestPrefix produces deterministic 8-char hex prefix")
    void testComputeDigestPrefix() {
        String digest1 = sanitizer.computeDigestPrefix("constant-string-123");
        String digest2 = sanitizer.computeDigestPrefix("constant-string-123");
        String digest3 = sanitizer.computeDigestPrefix("different-string-456");

        assertEquals(8, digest1.length());
        assertEquals(digest1, digest2);
        assertNotEquals(digest1, digest3);
    }
}
