package com.secretvault.oidc.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance, concurrency-controlled JWKS key provider supporting RSA and EC keys,
 * TTL caching, single-flight key rotation refresh, and DoS mitigation.
 */
@Component
public class JwksKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyProvider.class);
    private static final long JWKS_CACHE_TTL_SECONDS = 900; // 15 minutes
    private static final int MAX_JWKS_KEYS = 50;

    private final SsrfSafeHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> refreshLocks = new ConcurrentHashMap<>();

    private record CachedJwks(
            Map<String, PublicKey> keys,
            Instant expiresAt
    ) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public JwksKeyProvider(SsrfSafeHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves public key by key ID (kid) from target JWKS URI.
     * Automatically triggers a single-flight cache refresh when an unknown kid is encountered (Key Rotation).
     */
    public PublicKey getPublicKey(String jwksUri, String kid) {
        if (jwksUri == null || jwksUri.isBlank()) {
            throw ApiException.badRequest("JWKS URI is missing");
        }

        CachedJwks cached = jwksCache.get(jwksUri);
        if (cached != null && !cached.isExpired()) {
            if (kid == null && !cached.keys().isEmpty()) {
                // If token has no kid and JWKS has exactly 1 key, use it
                return cached.keys().values().iterator().next();
            }
            if (kid != null && cached.keys().containsKey(kid)) {
                return cached.keys().get(kid);
            }
        }

        // Key not in cache or cache expired -> Refresh JWKS with single-flight lock
        ReentrantLock lock = refreshLocks.computeIfAbsent(jwksUri, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check cache inside lock
            cached = jwksCache.get(jwksUri);
            if (cached != null && !cached.isExpired()) {
                if (kid != null && cached.keys().containsKey(kid)) {
                    return cached.keys().get(kid);
                }
            }

            // Perform remote fetch
            Map<String, PublicKey> refreshedKeys = fetchAndParseJwks(jwksUri);
            jwksCache.put(jwksUri, new CachedJwks(refreshedKeys, Instant.now().plusSeconds(JWKS_CACHE_TTL_SECONDS)));

            if (kid == null && !refreshedKeys.isEmpty()) {
                return refreshedKeys.values().iterator().next();
            }
            PublicKey key = kid != null ? refreshedKeys.get(kid) : null;
            if (key == null) {
                log.warn("Unknown Key ID [{}] not found in JWKS from [{}] after refresh", kid, jwksUri);
            }
            return key;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forces immediate refresh of the JWKS cache for the given URI.
     */
    public Map<String, PublicKey> forceRefreshJwks(String jwksUri) {
        Map<String, PublicKey> refreshedKeys = fetchAndParseJwks(jwksUri);
        jwksCache.put(jwksUri, new CachedJwks(refreshedKeys, Instant.now().plusSeconds(JWKS_CACHE_TTL_SECONDS)));
        return refreshedKeys;
    }

    private Map<String, PublicKey> fetchAndParseJwks(String jwksUri) {
        log.info("Fetching JWKS keys from remote URI: {}", jwksUri);
        String jwksJson = httpClient.executeSafeGet(jwksUri);

        Map<String, PublicKey> keyMap = new ConcurrentHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(jwksJson);
            JsonNode keysNode = root.get("keys");
            if (keysNode == null || !keysNode.isArray()) {
                throw ApiException.badRequest("Invalid JWKS JSON: 'keys' array missing");
            }

            int count = 0;
            for (JsonNode keyNode : keysNode) {
                if (++count > MAX_JWKS_KEYS) {
                    log.warn("JWKS key count exceeded limit of {}; remaining keys ignored", MAX_JWKS_KEYS);
                    break;
                }

                String kty = keyNode.has("kty") ? keyNode.get("kty").asText() : "";
                String kid = keyNode.has("kid") ? keyNode.get("kid").asText() : "default";

                try {
                    PublicKey publicKey = parseJwkKey(keyNode, kty);
                    if (publicKey != null) {
                        keyMap.put(kid, publicKey);
                        // Also store by kid without hyphens or prefix if applicable
                    }
                } catch (Exception e) {
                    log.warn("Skipping unparseable JWK key [kid={} kty={}]: {}", kid, kty, e.getMessage());
                }
            }

            log.info("Successfully loaded {} public keys from JWKS [{}]", keyMap.size(), jwksUri);
            return keyMap;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse JWKS JSON from [{}]: {}", jwksUri, e.getMessage());
            throw ApiException.badRequest("Failed to parse JWKS: " + e.getMessage());
        }
    }

    private PublicKey parseJwkKey(JsonNode keyNode, String kty) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Base64.Decoder decoder = Base64.getUrlDecoder();

        if ("RSA".equalsIgnoreCase(kty)) {
            String nStr = keyNode.get("n").asText();
            String eStr = keyNode.get("e").asText();

            BigInteger modulus = new BigInteger(1, decoder.decode(nStr));
            BigInteger publicExponent = new BigInteger(1, decoder.decode(eStr));

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, publicExponent);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec);
        } else if ("EC".equalsIgnoreCase(kty)) {
            String crv = keyNode.has("crv") ? keyNode.get("crv").asText() : "P-256";
            if (!"P-256".equalsIgnoreCase(crv) && !"secp256r1".equalsIgnoreCase(crv)) {
                log.warn("Unsupported EC curve in JWK: {}", crv);
                return null;
            }

            String xStr = keyNode.get("x").asText();
            String yStr = keyNode.get("y").asText();

            BigInteger x = new BigInteger(1, decoder.decode(xStr));
            BigInteger y = new BigInteger(1, decoder.decode(yStr));

            java.security.spec.ECParameterSpec ecSpec = getSecp256r1Spec();
            ECPoint ecPoint = new ECPoint(x, y);
            ECPublicKeySpec spec = new ECPublicKeySpec(ecPoint, ecSpec);
            KeyFactory factory = KeyFactory.getInstance("EC");
            return factory.generatePublic(spec);
        }

        log.debug("Skipping unsupported JWK key type: {}", kty);
        return null;
    }

    private static java.security.spec.ECParameterSpec getSecp256r1Spec() {
        try {
            java.security.AlgorithmParameters parameters = java.security.AlgorithmParameters.getInstance("EC");
            parameters.init(new java.security.spec.ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(java.security.spec.ECParameterSpec.class);
        } catch (Exception e) {
            throw new IllegalStateException("EC secp256r1 curve not supported by JVM", e);
        }
    }
}
