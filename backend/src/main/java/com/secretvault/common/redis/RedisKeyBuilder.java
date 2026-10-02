package com.secretvault.common.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Centralized, environment-isolated Redis key builder.
 * Enforces uniform namespace conventions across the entire SecretVault platform:
 * {@code secretvault:{environment}:{domain}:{category}:{identifier}}
 */
@Component
public class RedisKeyBuilder {

    private final String environment;

    public RedisKeyBuilder(@Value("${secretvault.redis.environment:${spring.profiles.active:local}}") String environment) {
        this.environment = sanitize(environment.split(",")[0].trim().toLowerCase());
    }

    /**
     * Builds a rate limit key.
     * Example: secretvault:local:ratelimit:auth_login:ip_127.0.0.1
     */
    public String rateLimitKey(String category, String identifier) {
        return buildKey("ratelimit", category, identifier);
    }

    /**
     * Builds a short-lived security state key.
     * Example: secretvault:local:security:mfa_challenge:c1a2-3b4c
     */
    public String securityStateKey(String category, String identifier) {
        return buildKey("security", category, identifier);
    }

    /**
     * Builds an application metadata cache key.
     * Example: secretvault:local:cache:workspace:ws-1234
     */
    public String cacheKey(String domain, String identifier) {
        return buildKey("cache", domain, identifier);
    }

    /**
     * Builds an idempotency key.
     * Example: secretvault:local:idempotency:secret_create:idem-789
     */
    public String idempotencyKey(String operation, String key) {
        return buildKey("idempotency", operation, key);
    }

    /**
     * Generic key constructor enforcing namespace invariants.
     */
    public String buildKey(String domain, String category, String identifier) {
        Objects.requireNonNull(domain, "Domain cannot be null");
        Objects.requireNonNull(category, "Category cannot be null");
        Objects.requireNonNull(identifier, "Identifier cannot be null");

        return "secretvault:" + environment + ":" + sanitize(domain) + ":" + sanitize(category) + ":" + sanitize(identifier);
    }

    public String getEnvironment() {
        return environment;
    }

    private static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "default";
        }
        // Replace spaces or multiple colons with single underscores, preserve safe chars
        return input.trim().replaceAll("[\\s:]+", "_").replaceAll("[^a-zA-Z0-9._-]", "");
    }
}
