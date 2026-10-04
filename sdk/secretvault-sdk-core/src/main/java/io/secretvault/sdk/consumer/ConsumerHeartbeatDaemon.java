package io.secretvault.sdk.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.secretvault.sdk.client.SecretVaultHttpClient;
import io.secretvault.sdk.exception.AuthenticationException;
import io.secretvault.sdk.exception.AuthorizationException;
import io.secretvault.sdk.exception.SecretVaultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resilient background consumer heartbeat daemon.
 *
 * <p>Periodically announces consumer liveness and acknowledges active secret versions
 * back to the SecretVault Consumer Registry so rotation engines know when workloads
 * have migrated to newly staged secret versions.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * The heartbeat payload contains STRICTLY:
 * <ul>
 *   <li>currentAcknowledgedVersion</li>
 *   <li>sdkVersion</li>
 *   <li>runtimeFramework</li>
 * </ul>
 * It NEVER transmits secret plaintext, ciphertext, DEKs, KEKs, authorization bearer tokens,
 * or credentials.
 */
public class ConsumerHeartbeatDaemon implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerHeartbeatDaemon.class);

    private final ConsumerHeartbeatConfig config;
    private final SecretVaultHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService externalScheduler;

    private final AtomicReference<Integer> acknowledgedVersion = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    private ScheduledExecutorService internalScheduler;
    private ScheduledFuture<?> scheduledTask;

    public ConsumerHeartbeatDaemon(ConsumerHeartbeatConfig config, SecretVaultHttpClient httpClient) {
        this(config, httpClient, null);
    }

    public ConsumerHeartbeatDaemon(
            ConsumerHeartbeatConfig config,
            SecretVaultHttpClient httpClient,
            ScheduledExecutorService scheduler
    ) {
        this.config = Objects.requireNonNull(config, "ConsumerHeartbeatConfig cannot be null");
        this.httpClient = Objects.requireNonNull(httpClient, "SecretVaultHttpClient cannot be null");
        this.externalScheduler = scheduler;
        this.objectMapper = new ObjectMapper();
        this.acknowledgedVersion.set(config.getInitialAcknowledgedVersion());
    }

    /**
     * Starts the periodic background heartbeat daemon.
     * Guarded against duplicate initialization.
     */
    public synchronized void start() {
        if (!config.isEnabled()) {
            log.info("ConsumerHeartbeatDaemon is disabled by configuration");
            return;
        }

        if (config.getConsumerId() == null) {
            log.warn("Cannot start ConsumerHeartbeatDaemon: consumerId is null");
            return;
        }

        if (running.get()) {
            log.warn("ConsumerHeartbeatDaemon is already running; ignoring duplicate start()");
            return;
        }

        ScheduledExecutorService executor = this.externalScheduler;
        if (executor == null) {
            this.internalScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "secretvault-consumer-heartbeat");
                    t.setDaemon(true);
                    return t;
                }
            });
            executor = this.internalScheduler;
        }

        long intervalMs = config.getInterval().toMillis();
        this.scheduledTask = executor.scheduleWithFixedDelay(
                this::sendHeartbeatSafe,
                0,
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        running.set(true);
        log.info("ConsumerHeartbeatDaemon started for consumer [{}] with interval {}ms",
                config.getConsumerId(), intervalMs);
    }

    /**
     * Gracefully stops the heartbeat scheduler without thread leaks or JVM shutdown blockage.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        log.info("Stopping ConsumerHeartbeatDaemon for consumer [{}]...", config.getConsumerId());

        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }

        if (internalScheduler != null && !internalScheduler.isShutdown()) {
            internalScheduler.shutdown();
            try {
                if (!internalScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    internalScheduler.shutdownNow();
                }
            } catch (InterruptedException ie) {
                internalScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                internalScheduler = null;
            }
        }

        log.info("ConsumerHeartbeatDaemon stopped successfully");
    }

    /**
     * Thread-safe update of current acknowledged secret version.
     */
    public void updateAcknowledgedVersion(int version) {
        acknowledgedVersion.set(version);
        log.debug("Consumer [{}] acknowledged secret version updated to {}", config.getConsumerId(), version);
    }

    public Integer getAcknowledgedVersion() {
        return acknowledgedVersion.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Synchronously sends a heartbeat to SecretVault backend.
     * Useful for manual triggers, post-rotation verification, and deterministic tests.
     */
    public ConsumerHeartbeatResult sendHeartbeatNow() {
        if (config.getConsumerId() == null) {
            return ConsumerHeartbeatResult.failure(acknowledgedVersion.get(), "consumerId is null");
        }

        String path = String.format("/api/v1/workspaces/%s/consumers/%s/heartbeat",
                config.getWorkspaceId() != null ? config.getWorkspaceId() : "default",
                config.getConsumerId()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentAcknowledgedVersion", acknowledgedVersion.get());
        payload.put("sdkVersion", config.getSdkVersion());
        payload.put("runtimeFramework", config.getRuntimeFramework());

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize heartbeat payload: {}", e.getMessage());
            return ConsumerHeartbeatResult.failure(acknowledgedVersion.get(), "Serialization error: " + e.getMessage());
        }

        int maxRetries = config.getMaxRetries();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                JsonNode response = httpClient.executeApi("POST", path, bodyJson, config.getWorkspaceId());
                consecutiveFailures.set(0);
                log.debug("Heartbeat acknowledged successfully for consumer [{}] version {}",
                        config.getConsumerId(), acknowledgedVersion.get());
                return ConsumerHeartbeatResult.success(acknowledgedVersion.get());
            } catch (AuthenticationException | AuthorizationException authEx) {
                // Fail closed on security/auth failure without hammering or exposing credentials
                consecutiveFailures.incrementAndGet();
                log.error("Consumer heartbeat authentication/authorization failure: {}", authEx.getMessage());
                return ConsumerHeartbeatResult.failure(acknowledgedVersion.get(), "Auth failure: " + authEx.getMessage());
            } catch (SecretVaultException sve) {
                lastException = sve;
                consecutiveFailures.incrementAndGet();
                log.warn("Consumer heartbeat transient failure (attempt {}/{}): {}", attempt, maxRetries, sve.getMessage());
                if (attempt < maxRetries) {
                    sleepBackoff(attempt);
                }
            } catch (Exception ex) {
                lastException = ex;
                consecutiveFailures.incrementAndGet();
                log.warn("Consumer heartbeat network error (attempt {}/{}): {}", attempt, maxRetries, ex.getMessage());
                if (attempt < maxRetries) {
                    sleepBackoff(attempt);
                }
            }
        }

        String errMsg = lastException != null ? lastException.getMessage() : "Unknown heartbeat failure";
        return ConsumerHeartbeatResult.failure(acknowledgedVersion.get(), errMsg);
    }

    private void sendHeartbeatSafe() {
        try {
            ConsumerHeartbeatResult result = sendHeartbeatNow();
            if (!result.success()) {
                log.debug("Background heartbeat failed for consumer [{}]: {}", config.getConsumerId(), result.errorMessage());
            }
        } catch (Throwable t) {
            // Safety guard: never let uncaught exceptions kill the scheduled executor
            log.error("Unexpected error in background consumer heartbeat loop: {}", t.getMessage());
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            long backoffMs = (long) (100 * Math.pow(2, attempt - 1));
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
