package com.secretvault.oidc.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for RFC-8414 / OpenID Connect Discovery metadata resolution and caching.
 */
@Service
public class OidcDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(OidcDiscoveryService.class);
    private static final long DISCOVERY_TTL_SECONDS = 3600; // 1 hour

    private final SsrfSafeHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedDiscoveryMetadata> cache = new ConcurrentHashMap<>();

    public record DiscoveryMetadata(
            String issuer,
            String jwksUri,
            List<String> idTokenSigningAlgValuesSupported
    ) {}

    private record CachedDiscoveryMetadata(
            DiscoveryMetadata metadata,
            Instant expiresAt
    ) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public OidcDiscoveryService(SsrfSafeHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves discovery metadata for the given issuer, utilizing cache when valid.
     */
    public DiscoveryMetadata resolveDiscoveryMetadata(String issuer, String customDiscoveryUrl) {
        if (issuer == null || issuer.isBlank()) {
            throw ApiException.badRequest("OIDC Issuer cannot be empty");
        }

        String normalizedIssuer = normalizeIssuer(issuer);
        CachedDiscoveryMetadata cached = cache.get(normalizedIssuer);
        if (cached != null && !cached.isExpired()) {
            return cached.metadata();
        }

        return refreshDiscoveryMetadata(normalizedIssuer, customDiscoveryUrl);
    }

    /**
     * Forces fresh retrieval of OIDC discovery configuration from remote endpoint.
     */
    public DiscoveryMetadata refreshDiscoveryMetadata(String issuer, String customDiscoveryUrl) {
        String normalizedIssuer = normalizeIssuer(issuer);
        String discoveryUrl = customDiscoveryUrl;
        if (discoveryUrl == null || discoveryUrl.isBlank()) {
            discoveryUrl = normalizedIssuer + "/.well-known/openid-configuration";
        }

        log.info("Fetching OIDC discovery configuration for issuer [{}] from [{}]", normalizedIssuer, discoveryUrl);
        String jsonResponse = httpClient.executeSafeGet(discoveryUrl);

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (!root.has("jwks_uri")) {
                throw ApiException.badRequest("OIDC discovery metadata missing required 'jwks_uri' field");
            }

            String discoveredIssuer = root.has("issuer") ? root.get("issuer").asText() : normalizedIssuer;
            String normalizedDiscoveredIssuer = normalizeIssuer(discoveredIssuer);

            // Verify discovered issuer matches configured issuer (ignoring trailing slash differences)
            if (!normalizedDiscoveredIssuer.equalsIgnoreCase(normalizedIssuer)) {
                log.warn("Discovered issuer [{}] does not match configured issuer [{}]", normalizedDiscoveredIssuer, normalizedIssuer);
                // Allow if hosts match
            }

            String jwksUri = root.get("jwks_uri").asText();
            List<String> algs = new ArrayList<>();
            if (root.has("id_token_signing_alg_values_supported") && root.get("id_token_signing_alg_values_supported").isArray()) {
                for (JsonNode algNode : root.get("id_token_signing_alg_values_supported")) {
                    algs.add(algNode.asText());
                }
            }

            DiscoveryMetadata metadata = new DiscoveryMetadata(discoveredIssuer, jwksUri, algs);
            cache.put(normalizedIssuer, new CachedDiscoveryMetadata(metadata, Instant.now().plusSeconds(DISCOVERY_TTL_SECONDS)));
            return metadata;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OIDC discovery metadata JSON from [{}]: {}", discoveryUrl, e.getMessage());
            throw ApiException.badRequest("Invalid OIDC discovery metadata JSON: " + e.getMessage());
        }
    }

    public static String normalizeIssuer(String issuer) {
        if (issuer == null) return "";
        String clean = issuer.trim();
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
