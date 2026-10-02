package com.secretvault.common.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Top-level Redis configuration properties for SecretVault.
 */
@Component
@ConfigurationProperties(prefix = "secretvault.redis")
public class RedisProperties {

    private String environment = "local";
    private RateLimit rateLimit = new RateLimit();
    private Cache cache = new Cache();
    private Ttl ttl = new Ttl();

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Ttl getTtl() {
        return ttl;
    }

    public void setTtl(Ttl ttl) {
        this.ttl = ttl;
    }

    public static class RateLimit {
        private boolean enabled = true;
        private boolean failOpen = false; // Default fail-closed for security

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }

    public static class Cache {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Ttl {
        private Duration defaultRateLimit = Duration.ofSeconds(60);
        private Duration mfaChallenge = Duration.ofMinutes(5);
        private Duration stepUp = Duration.ofMinutes(10);
        private Duration metadataCache = Duration.ofMinutes(5);
        private Duration idempotency = Duration.ofMinutes(2);

        public Duration getDefaultRateLimit() {
            return defaultRateLimit;
        }

        public void setDefaultRateLimit(Duration defaultRateLimit) {
            this.defaultRateLimit = defaultRateLimit;
        }

        public Duration getMfaChallenge() {
            return mfaChallenge;
        }

        public void setMfaChallenge(Duration mfaChallenge) {
            this.mfaChallenge = mfaChallenge;
        }

        public Duration getStepUp() {
            return stepUp;
        }

        public void setStepUp(Duration stepUp) {
            this.stepUp = stepUp;
        }

        public Duration getMetadataCache() {
            return metadataCache;
        }

        public void setMetadataCache(Duration metadataCache) {
            this.metadataCache = metadataCache;
        }

        public Duration getIdempotency() {
            return idempotency;
        }

        public void setIdempotency(Duration idempotency) {
            this.idempotency = idempotency;
        }
    }
}
