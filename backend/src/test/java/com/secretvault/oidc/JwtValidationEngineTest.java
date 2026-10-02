package com.secretvault.oidc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.oidc.entity.OidcProvider;
import com.secretvault.oidc.model.OidcProviderType;
import com.secretvault.oidc.security.JwksKeyProvider;
import com.secretvault.oidc.security.JwtValidationEngine;
import com.secretvault.oidc.security.OidcDiscoveryService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class JwtValidationEngineTest {

    private JwksKeyProvider keyProvider;
    private OidcDiscoveryService discoveryService;
    private ObjectMapper objectMapper;
    private JwtValidationEngine validationEngine;
    private KeyPair keyPair;
    private OidcProvider provider;
    private UUID workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        keyProvider = Mockito.mock(JwksKeyProvider.class);
        discoveryService = Mockito.mock(OidcDiscoveryService.class);
        objectMapper = new ObjectMapper();
        validationEngine = new JwtValidationEngine(keyProvider, discoveryService, objectMapper);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        workspaceId = UUID.randomUUID();
        provider = new OidcProvider();
        provider.setId(UUID.randomUUID());
        provider.setWorkspaceId(workspaceId);
        provider.setName("GitHub Actions Test");
        provider.setIssuer("https://token.actions.githubusercontent.com");
        provider.setAudience("https://github.com/secretvault");
        provider.setDiscoveryUrl("https://token.actions.githubusercontent.com/.well-known/openid-configuration");
        provider.setJwksUrl("https://token.actions.githubusercontent.com/.well-known/jwks");
        provider.setProviderType(OidcProviderType.GITHUB_ACTIONS);
        provider.setAllowedAlgorithms("RS256,ES256");

        when(keyProvider.getPublicKey(any(), any())).thenReturn(keyPair.getPublic());
    }

    private String createSignedJwt(String keyId, String issuer, String audience, Date exp, Date nbf, Map<String, Object> customClaims) {
        var builder = Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("repo:Hrushi4151/Rally:ref:refs/heads/main")
                .issuedAt(new Date())
                .expiration(exp != null ? exp : new Date(System.currentTimeMillis() + 600000))
                .id(UUID.randomUUID().toString())
                .signWith(keyPair.getPrivate());

        if (nbf != null) {
            builder.notBefore(nbf);
        }

        if (customClaims != null) {
            customClaims.forEach(builder::claim);
        }

        return builder.compact();
    }

    @Test
    @DisplayName("Valid JWT signed by trusted RSA key parses and validates correctly")
    void testValidJwt() {
        String token = createSignedJwt("test-key-1", "https://token.actions.githubusercontent.com", "https://github.com/secretvault", null, null, Map.of(
                "repository", "Hrushi4151/Rally",
                "ref", "refs/heads/main"
        ));

        Map<String, Object> claims = validationEngine.validateAndExtractClaims(token, provider);
        assertNotNull(claims);
        assertEquals("Hrushi4151/Rally", claims.get("repository"));
        assertEquals("refs/heads/main", claims.get("ref"));
    }

    @Test
    @DisplayName("Reject token when issuer does not match configured provider issuer")
    void testIssuerMismatch() {
        String token = createSignedJwt("test-key-1", "https://malicious-issuer.com", "https://github.com/secretvault", null, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                validationEngine.validateAndExtractClaims(token, provider));
        assertTrue(ex.getMessage().contains("issuer mismatch"));
    }

    @Test
    @DisplayName("Reject token when audience does not match configured audience")
    void testAudienceMismatch() {
        String token = createSignedJwt("test-key-1", "https://token.actions.githubusercontent.com", "wrong-audience", null, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                validationEngine.validateAndExtractClaims(token, provider));
        assertTrue(ex.getMessage().contains("audience mismatch"));
    }

    @Test
    @DisplayName("Reject expired JWT token (exp in past)")
    void testExpiredToken() {
        Date pastDate = new Date(System.currentTimeMillis() - 120000); // 2 minutes ago
        String token = createSignedJwt("test-key-1", "https://token.actions.githubusercontent.com", "https://github.com/secretvault", pastDate, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                validationEngine.validateAndExtractClaims(token, provider));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Reject JWT with not-before (nbf) in the future")
    void testFutureNbfToken() {
        Date futureDate = new Date(System.currentTimeMillis() + 300000); // 5 minutes in future
        String token = createSignedJwt("test-key-1", "https://token.actions.githubusercontent.com", "https://github.com/secretvault", null, futureDate, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                validationEngine.validateAndExtractClaims(token, provider));
        assertTrue(ex.getMessage().contains("not yet valid"));
    }

    @Test
    @DisplayName("Reject alg=none and algorithm confusion attacks")
    void testAlgNoneRejected() {
        // Plain JWT with alg=none
        String algNoneToken = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJpc3MiOiJodHRwczovL3Rva2VuLmFjdGlvbnMuZ2l0aHVidXNlcmNvbnRlbnQuY29tIiwic3ViIjoidGVzdCJ9.";

        assertThrows(ApiException.class, () ->
                validationEngine.validateAndExtractClaims(algNoneToken, provider));
    }

    @Test
    @DisplayName("Reject oversized JWT tokens (> 16KB)")
    void testOversizedJwtRejected() {
        String oversized = "header." + "a".repeat(20000) + ".signature";

        ApiException ex = assertThrows(ApiException.class, () ->
                validationEngine.validateAndExtractClaims(oversized, provider));
        assertTrue(ex.getMessage().contains("exceeds maximum allowed size"));
    }
}
