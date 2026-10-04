package io.secretvault.sdk.client;

import io.secretvault.sdk.auth.CredentialProviderChain;
import io.secretvault.sdk.auth.CredentialsProvider;
import io.secretvault.sdk.auth.StaticTokenProvider;
import io.secretvault.sdk.exception.ConfigurationException;
import io.secretvault.sdk.model.SecretScope;
import io.secretvault.sdk.resilience.ResiliencePolicy;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for {@link io.secretvault.sdk.api.SecretVaultClient}.
 */
public final class SdkConfig {

    private final URI endpoint;
    private final CredentialsProvider credentialsProvider;
    private final SecretScope defaultScope;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final boolean cacheEnabled;
    private final Duration cacheTtl;
    private final int cacheMaxEntries;
    private final ResiliencePolicy resiliencePolicy;
    private final Duration maxStaleDuration;
    private final int retryAttempts;
    private final boolean circuitBreakerEnabled;
    private final boolean allowHttp;
    private final String applicationName;
    private final String applicationVersion;
    private final io.secretvault.sdk.consumer.ConsumerHeartbeatConfig heartbeatConfig;

    private SdkConfig(Builder builder) {
        this.endpoint = Objects.requireNonNull(builder.endpoint, "SecretVault endpoint URI is required");
        this.credentialsProvider = builder.credentialsProvider != null ? builder.credentialsProvider : CredentialProviderChain.defaultChain();
        this.defaultScope = builder.defaultScope;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.cacheEnabled = builder.cacheEnabled;
        this.cacheTtl = builder.cacheTtl;
        this.cacheMaxEntries = builder.cacheMaxEntries;
        this.resiliencePolicy = builder.resiliencePolicy;
        this.maxStaleDuration = builder.maxStaleDuration;
        this.retryAttempts = builder.retryAttempts;
        this.circuitBreakerEnabled = builder.circuitBreakerEnabled;
        this.allowHttp = builder.allowHttp;
        this.applicationName = builder.applicationName;
        this.applicationVersion = builder.applicationVersion;
        this.heartbeatConfig = builder.heartbeatConfig;

        validate();
    }

    private void validate() {
        if (!allowHttp && "http".equalsIgnoreCase(endpoint.getScheme())) {
            throw new ConfigurationException("Insecure HTTP endpoint rejected in production mode. Use HTTPS or set allowHttp(true).");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new ConfigurationException("connectTimeout must be positive");
        }
        if (readTimeout.isNegative() || readTimeout.isZero()) {
            throw new ConfigurationException("readTimeout must be positive");
        }
        if (cacheTtl.isNegative()) {
            throw new ConfigurationException("cacheTtl cannot be negative");
        }
        if (resiliencePolicy == ResiliencePolicy.FAIL_OPEN_WITH_CACHE && maxStaleDuration.isNegative()) {
            throw new ConfigurationException("maxStaleDuration must be non-negative when FAIL_OPEN_WITH_CACHE is configured");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public URI getEndpoint() { return endpoint; }
    public CredentialsProvider getCredentialsProvider() { return credentialsProvider; }
    public SecretScope getDefaultScope() { return defaultScope; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public boolean isCacheEnabled() { return cacheEnabled; }
    public Duration getCacheTtl() { return cacheTtl; }
    public int getCacheMaxEntries() { return cacheMaxEntries; }
    public ResiliencePolicy getResiliencePolicy() { return resiliencePolicy; }
    public Duration getMaxStaleDuration() { return maxStaleDuration; }
    public int getRetryAttempts() { return retryAttempts; }
    public boolean isCircuitBreakerEnabled() { return circuitBreakerEnabled; }
    public boolean isAllowHttp() { return allowHttp; }
    public String getApplicationName() { return applicationName; }
    public String getApplicationVersion() { return applicationVersion; }
    public io.secretvault.sdk.consumer.ConsumerHeartbeatConfig getHeartbeatConfig() { return heartbeatConfig; }

    public static final class Builder {
        private URI endpoint = URI.create("http://localhost:8080");
        private CredentialsProvider credentialsProvider;
        private SecretScope defaultScope;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(10);
        private boolean cacheEnabled = true;
        private Duration cacheTtl = Duration.ofSeconds(60);
        private int cacheMaxEntries = 500;
        private ResiliencePolicy resiliencePolicy = ResiliencePolicy.FAIL_CLOSED;
        private Duration maxStaleDuration = Duration.ofMinutes(5);
        private int retryAttempts = 3;
        private boolean circuitBreakerEnabled = true;
        private boolean allowHttp = true; // default true for local dev, configurable
        private String applicationName = "secretvault-app";
        private String applicationVersion = "1.0.0";
        private io.secretvault.sdk.consumer.ConsumerHeartbeatConfig heartbeatConfig;

        public Builder endpoint(String endpoint) {
            this.endpoint = URI.create(endpoint);
            return this;
        }

        public Builder endpoint(URI endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder credentials(CredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        public Builder token(String token) {
            this.credentialsProvider = new StaticTokenProvider(token);
            return this;
        }

        public Builder defaultScope(String workspace, String project, String environment) {
            this.defaultScope = SecretScope.of(workspace, project, environment);
            return this;
        }

        public Builder defaultScope(SecretScope scope) {
            this.defaultScope = scope;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        public Builder cacheMaxEntries(int cacheMaxEntries) {
            this.cacheMaxEntries = cacheMaxEntries;
            return this;
        }

        public Builder resiliencePolicy(ResiliencePolicy resiliencePolicy) {
            this.resiliencePolicy = resiliencePolicy;
            return this;
        }

        public Builder maxStaleDuration(Duration maxStaleDuration) {
            this.maxStaleDuration = maxStaleDuration;
            return this;
        }

        public Builder retryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
            return this;
        }

        public Builder circuitBreakerEnabled(boolean circuitBreakerEnabled) {
            this.circuitBreakerEnabled = circuitBreakerEnabled;
            return this;
        }

        public Builder allowHttp(boolean allowHttp) {
            this.allowHttp = allowHttp;
            return this;
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder applicationVersion(String applicationVersion) {
            this.applicationVersion = applicationVersion;
            return this;
        }

        public Builder heartbeat(io.secretvault.sdk.consumer.ConsumerHeartbeatConfig heartbeatConfig) {
            this.heartbeatConfig = heartbeatConfig;
            return this;
        }

        public Builder consumerHeartbeat(java.util.UUID consumerId, Duration interval) {
            this.heartbeatConfig = io.secretvault.sdk.consumer.ConsumerHeartbeatConfig.builder()
                    .consumerId(consumerId)
                    .interval(interval)
                    .enabled(true)
                    .build();
            return this;
        }

        public SdkConfig build() {
            return new SdkConfig(this);
        }
    }
}
