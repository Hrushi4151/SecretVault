package com.secretvault.oidc.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.oidc.entity.OidcProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.PrematureJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Strict, cryptographic JWT validation engine for OIDC ID tokens.
 * Enforces size limits, rejects alg=none, blocks algorithm confusion,
 * verifies cryptographic signatures against JWKS, and validates issuer, audience, exp, and nbf.
 */
@Component
public class JwtValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationEngine.class);
    private static final int MAX_JWT_LENGTH = 16 * 1024; // 16 KB max token size
    private static final long CLOCK_SKEW_SECONDS = 60; // 60s tolerance

    private final JwksKeyProvider jwksKeyProvider;
    private final OidcDiscoveryService discoveryService;
    private final ObjectMapper objectMapper;

    public JwtValidationEngine(JwksKeyProvider jwksKeyProvider, OidcDiscoveryService discoveryService, ObjectMapper objectMapper) {
        this.jwksKeyProvider = jwksKeyProvider;
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates incoming raw OIDC token against provider configuration.
     * Returns extracted, verified claims map.
     */
    public Map<String, Object> validateAndExtractClaims(String rawJwt, OidcProvider provider) {
        if (rawJwt == null || rawJwt.isBlank()) {
            throw ApiException.unauthorized("OIDC token is empty");
        }

        String token = rawJwt.trim();
        if (token.startsWith("Bearer ") || token.startsWith("bearer ")) {
            token = token.substring(7).trim();
        }

        if (token.length() > MAX_JWT_LENGTH) {
            throw ApiException.unauthorized("OIDC token exceeds maximum allowed size of 16KB");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw ApiException.unauthorized("Malformed JWT token: must contain exactly 3 segments");
        }

        // 1. Inspect Header without trusting claims
        String headerJson;
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            headerJson = new String(headerBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw ApiException.unauthorized("Failed to base64url decode JWT header");
        }

        String alg;
        String kid = null;
        try {
            JsonNode headerNode = objectMapper.readTree(headerJson);
            if (!headerNode.has("alg")) {
                throw ApiException.unauthorized("JWT header is missing 'alg' parameter");
            }
            alg = headerNode.get("alg").asText();
            if (headerNode.has("kid")) {
                kid = headerNode.get("kid").asText();
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.unauthorized("Invalid JWT header JSON");
        }

        // 2. Reject alg=none and unsupported algorithms (Algorithm Confusion Defense)
        if ("none".equalsIgnoreCase(alg)) {
            throw ApiException.unauthorized("Rejected insecure JWT: alg=none is forbidden");
        }

        Set<String> allowedAlgs = getAllowedAlgorithms(provider);
        if (!allowedAlgs.contains(alg.toUpperCase())) {
            throw ApiException.unauthorized("JWT algorithm [" + alg + "] is not permitted. Allowed algorithms: " + allowedAlgs);
        }

        // 3. Resolve JWKS URI
        String jwksUri = provider.getJwksUrl();
        if (jwksUri == null || jwksUri.isBlank()) {
            // Resolve via discovery
            OidcDiscoveryService.DiscoveryMetadata discovery = discoveryService.resolveDiscoveryMetadata(
                    provider.getIssuer(), provider.getDiscoveryUrl()
            );
            jwksUri = discovery.jwksUri();
        }

        // 4. Retrieve public key from JWKS
        PublicKey publicKey = jwksKeyProvider.getPublicKey(jwksUri, kid);
        if (publicKey == null) {
            log.warn("Public key with kid [{}] not found for provider [{}] at JWKS URI [{}]", kid, provider.getName(), jwksUri);
            throw ApiException.unauthorized("OIDC_UNKNOWN_KEY: No matching public key found for key ID: " + kid);
        }

        // 5. Cryptographic Signature & Expiration Verification
        Claims claims;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(publicKey)
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(token);
            claims = jws.getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("OIDC token expired: {}", e.getMessage());
            throw ApiException.unauthorized("OIDC_EXPIRED: Token has expired at " + e.getClaims().getExpiration());
        } catch (PrematureJwtException e) {
            log.warn("OIDC token not yet valid: {}", e.getMessage());
            throw ApiException.unauthorized("OIDC_NOT_YET_VALID: Token is not yet valid before " + e.getClaims().getNotBefore());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Cryptographic signature verification failed: {}", e.getMessage());
            throw ApiException.unauthorized("OIDC_INVALID_SIGNATURE: Token signature validation failed");
        }

        // 6. Strict Issuer Validation
        String tokenIssuer = claims.getIssuer();
        if (tokenIssuer == null || !isIssuerMatch(tokenIssuer, provider.getIssuer())) {
            log.warn("Issuer mismatch: token issuer [{}] != configured issuer [{}]", tokenIssuer, provider.getIssuer());
            throw ApiException.unauthorized("OIDC_INVALID_ISSUER: Token issuer does not match configured provider issuer (issuer mismatch)");
        }

        // 7. Strict Audience Validation
        validateAudience(claims, provider.getAudience());

        // 8. Sanity check: Issued At (iat)
        Date iat = claims.getIssuedAt();
        if (iat != null) {
            Instant iatInstant = iat.toInstant();
            if (iatInstant.isAfter(Instant.now().plusSeconds(CLOCK_SKEW_SECONDS))) {
                throw ApiException.unauthorized("OIDC_NOT_YET_VALID: Token issued-at timestamp is in the future");
            }
        }

        // 9. Sanity check: Not Before (nbf)
        Date nbf = claims.getNotBefore();
        if (nbf != null) {
            Instant nbfInstant = nbf.toInstant();
            if (nbfInstant.isAfter(Instant.now().plusSeconds(CLOCK_SKEW_SECONDS))) {
                throw ApiException.unauthorized("OIDC_NOT_YET_VALID: Token is not yet valid before " + nbf);
            }
        }

        return new HashMap<>(claims);
    }

    private void validateAudience(Claims claims, String expectedAudience) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            return;
        }

        Object audClaim = claims.get("aud");
        if (audClaim == null) {
            throw ApiException.unauthorized("OIDC_INVALID_AUDIENCE: Token missing required audience (aud) claim");
        }

        if (audClaim instanceof String audStr) {
            if (!expectedAudience.equals(audStr)) {
                throw ApiException.unauthorized("OIDC_INVALID_AUDIENCE: Token audience [" + audStr + "] does not match expected [" + expectedAudience + "] (audience mismatch)");
            }
        } else if (audClaim instanceof Collection<?> audList) {
            boolean matched = false;
            for (Object item : audList) {
                if (expectedAudience.equals(String.valueOf(item))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw ApiException.unauthorized("OIDC_INVALID_AUDIENCE: Token audience list " + audList + " does not contain expected [" + expectedAudience + "] (audience mismatch)");
            }
        } else {
            if (!expectedAudience.equals(String.valueOf(audClaim))) {
                throw ApiException.unauthorized("OIDC_INVALID_AUDIENCE: Token audience mismatch");
            }
        }
    }

    private boolean isIssuerMatch(String tokenIssuer, String configuredIssuer) {
        String t = OidcDiscoveryService.normalizeIssuer(tokenIssuer);
        String c = OidcDiscoveryService.normalizeIssuer(configuredIssuer);
        return t.equalsIgnoreCase(c);
    }

    private Set<String> getAllowedAlgorithms(OidcProvider provider) {
        String allowed = provider.getAllowedAlgorithms();
        if (allowed == null || allowed.isBlank()) {
            return Set.of("RS256", "ES256");
        }
        Set<String> set = new HashSet<>();
        for (String alg : allowed.split(",")) {
            if (!alg.trim().isEmpty()) {
                set.add(alg.trim().toUpperCase());
            }
        }
        return set;
    }
}
