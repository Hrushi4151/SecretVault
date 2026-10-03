package com.secretvault.rotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.model.SecretType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretGenerationEngineTest {

    private SecretGenerationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SecretGenerationEngine(new ObjectMapper());
    }

    @Test
    @DisplayName("Should generate secure alphanumeric passwords with special characters")
    void testGeneratePassword() {
        String password = engine.generatePassword(32, true, true);
        assertNotNull(password);
        assertEquals(32, password.length());

        assertTrue(password.chars().anyMatch(Character::isDigit));
        assertTrue(password.chars().anyMatch(Character::isLetter));
    }

    @Test
    @DisplayName("Should generate cryptographically strong API keys with prefix")
    void testGenerateApiKey() {
        String apiKey = engine.generateApiKey("sk_live_", 48);
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("sk_live_"));
        assertTrue(apiKey.length() > 50);
    }

    @Test
    @DisplayName("Should generate URL-safe bearer tokens")
    void testGenerateToken() {
        String token = engine.generateToken(32);
        assertNotNull(token);
        assertFalse(token.contains(" "));
        assertFalse(token.contains("\n"));
    }

    @Test
    @DisplayName("Should generate RSA 2048-bit SSH keypair in standard format")
    void testGenerateSshKeyPair() {
        String pemKey = engine.generateRsaKeyPairPem(2048);
        assertNotNull(pemKey);
        assertTrue(pemKey.contains("BEGIN PRIVATE KEY"));
        assertTrue(pemKey.contains("END PRIVATE KEY"));
    }

    @Test
    @DisplayName("Should generate high-entropy database credentials with structured JSON")
    void testGenerateDatabaseCredentials() {
        String dbCred = engine.generateSecret(SecretType.DATABASE_CREDENTIAL, "{\"length\": 32}");
        assertNotNull(dbCred);
        assertTrue(dbCred.length() >= 20);
    }

    @Test
    @DisplayName("Should generate secrets based on SecretType enumeration")
    void testGenerateByType() {
        String apiKey = engine.generateSecret(SecretType.API_KEY, null);
        assertNotNull(apiKey);

        String token = engine.generateSecret(SecretType.TOKEN, null);
        assertNotNull(token);

        String password = engine.generateSecret(SecretType.PASSWORD, null);
        assertNotNull(password);
        assertTrue(password.length() >= 16);
    }
}
