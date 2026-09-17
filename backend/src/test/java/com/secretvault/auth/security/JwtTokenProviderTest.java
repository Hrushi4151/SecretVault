package com.secretvault.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String testSecret = "test_super_secret_jwt_signing_key_for_unit_tests_256_bits_long";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(testSecret, 3600);
    }

    @Test
    @DisplayName("Should generate valid JWT access token and extract claims accurately")
    void testGenerateAndValidateAccessToken() {
        UUID userId = UUID.randomUUID();
        String email = "alice@example.com";
        String fullName = "Alice Vance";

        String token = tokenProvider.generateAccessToken(userId, email, fullName);

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals(userId, tokenProvider.getUserIdFromToken(token));
        assertEquals(email, tokenProvider.getEmailFromToken(token));
    }

    @Test
    @DisplayName("Should reject invalid or tampered JWT token")
    void testRejectInvalidToken() {
        assertFalse(tokenProvider.validateToken("invalid.token.string"));
        assertFalse(tokenProvider.validateToken(""));
        assertFalse(tokenProvider.validateToken(null));
    }

    @Test
    @DisplayName("Should generate unique refresh tokens and consistent SHA-256 hashes")
    void testRefreshTokenGenerationAndHashing() {
        String token1 = tokenProvider.generateRefreshToken();
        String token2 = tokenProvider.generateRefreshToken();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);

        String hash1 = tokenProvider.hashToken(token1);
        String hash2 = tokenProvider.hashToken(token1);
        assertEquals(hash1, hash2, "SHA-256 hash must be deterministic");
        assertEquals(64, hash1.length(), "SHA-256 hex string must be 64 chars long");
    }

    @Test
    @DisplayName("Should reject expired tokens")
    void testRejectExpiredToken() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(testSecret, 1);
        UUID userId = UUID.randomUUID();
        String token = shortLivedProvider.generateAccessToken(userId, "bob@example.com", "Bob");

        assertTrue(shortLivedProvider.validateToken(token));
        Thread.sleep(1100);
        assertFalse(shortLivedProvider.validateToken(token));
    }
}
