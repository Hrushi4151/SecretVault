package io.secretvault.sdk.api;

import io.secretvault.sdk.cache.SecretCache;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.client.SecretVaultHttpClient;
import io.secretvault.sdk.observability.DefaultSdkMetrics;
import io.secretvault.sdk.observability.SdkMetrics;
import io.secretvault.sdk.resilience.CircuitBreaker;
import io.secretvault.sdk.resilience.RequestCoalescer;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for consuming SecretVault secrets in Java 21+ applications.
 */
public class SecretVaultClient implements AutoCloseable {

    private final SdkConfig config;
    private final SecretCache cache;
    private final CircuitBreaker circuitBreaker;
    private final SdkMetrics metrics;
    private final RequestCoalescer coalescer;
    private final SecretVaultHttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final SecretsApi secretsApi;
    private final RepositorySecurityApi repositorySecurityApi;
    private final io.secretvault.sdk.consumer.ConsumerHeartbeatDaemon heartbeatDaemon;

    private SecretVaultClient(SdkConfig config) {
        this.config = Objects.requireNonNull(config, "SdkConfig cannot be null");
        this.cache = new SecretCache(config.getCacheMaxEntries(), config.getCacheTtl(), config.getMaxStaleDuration());
        this.circuitBreaker = new CircuitBreaker();
        this.metrics = new DefaultSdkMetrics();
        this.coalescer = new RequestCoalescer();
        this.httpClient = new SecretVaultHttpClient(config, this.circuitBreaker, this.metrics);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "secretvault-sdk-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.secretsApi = new DefaultSecretsApi(config, httpClient, cache, coalescer, metrics, scheduler);
        this.repositorySecurityApi = new DefaultRepositorySecurityApi(config, httpClient);

        if (config.getHeartbeatConfig() != null && config.getHeartbeatConfig().isEnabled()) {
            this.heartbeatDaemon = new io.secretvault.sdk.consumer.ConsumerHeartbeatDaemon(
                    config.getHeartbeatConfig(),
                    this.httpClient
            );
            this.heartbeatDaemon.start();
        } else {
            this.heartbeatDaemon = null;
        }
    }

    public static SecretVaultClient create(SdkConfig config) {
        return new SecretVaultClient(config);
    }

    public static SdkConfig.Builder builder() {
        return SdkConfig.builder();
    }

    public SecretsApi secrets() {
        return secretsApi;
    }

    public RepositorySecurityApi repositorySecurity() {
        return repositorySecurityApi;
    }

    public boolean ping() {
        return httpClient.ping();
    }

    public SecretCache getCache() {
        return cache;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public SdkMetrics getMetrics() {
        return metrics;
    }

    public SdkConfig getConfig() {
        return config;
    }

    public java.util.Optional<io.secretvault.sdk.consumer.ConsumerHeartbeatDaemon> getHeartbeatDaemon() {
        return java.util.Optional.ofNullable(heartbeatDaemon);
    }

    @Override
    public void close() {
        if (heartbeatDaemon != null) {
            heartbeatDaemon.stop();
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException ignored) {
                scheduler.shutdownNow();
            }
        }
        if (config.getCredentialsProvider() != null) {
            try {
                config.getCredentialsProvider().close();
            } catch (Exception ignored) {}
        }
        cache.clear();
    }
}
