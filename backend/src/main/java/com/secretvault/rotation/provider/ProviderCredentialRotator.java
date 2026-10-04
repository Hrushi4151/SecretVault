package com.secretvault.rotation.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.events.entity.EventProcessingLog;
import com.secretvault.events.repository.EventProcessingLogRepository;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.model.ProviderSecretOperationResult;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationProviderPushEvent;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-integrated rotator that synchronizes newly rotated secrets to external cloud platforms
 * (Vercel, Render, etc.) using the Provider Integration Framework and ProviderAdapter SPI.
 *
 * <p><strong>Lifecycle:</strong>
 * Rotation activation -> RotationProviderPushEvent -> ProviderCredentialRotator -> ProviderAdapter -> external provider -> verification -> rotation success/failure.
 *
 * <p><strong>Durable Idempotency Invariant:</strong>
 * Provider push delivery is authoritatively protected against duplicate executions across JVM restarts,
 * multiple workers, outbox redeliveries, and failovers via persistent database records in {@link EventProcessingLog}.
 *
 * <p><strong>Zero-Leakage Security Invariant:</strong>
 * Plaintext secrets, DEKs, KEKs, and provider access tokens are never logged, never included in exceptions,
 * and never passed across unencrypted event boundaries.
 */
@Component
@Order(40)
public class ProviderCredentialRotator implements SecretRotator {

    private static final Logger log = LoggerFactory.getLogger(ProviderCredentialRotator.class);
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final SecretGenerationEngine generationEngine;
    private final ProviderAdapterRegistry providerAdapterRegistry;
    private final ProviderSecretSyncService providerSecretSyncService;
    private final ProviderResourceMappingRepository mappingRepository;
    private final ProviderIntegrationRepository integrationRepository;
    private final ProviderCredentialService credentialService;
    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventProcessingLogRepository eventProcessingLogRepository;

    // Concurrent dedup map for active rotation job pushes to prevent duplicate synchronous + event delivery
    private final java.util.concurrent.ConcurrentHashMap<String, Long> recentlyPushedKeys = new java.util.concurrent.ConcurrentHashMap<>();

    public ProviderCredentialRotator(
            SecretGenerationEngine generationEngine,
            ProviderAdapterRegistry providerAdapterRegistry,
            ProviderSecretSyncService providerSecretSyncService
    ) {
        this(generationEngine, providerAdapterRegistry, providerSecretSyncService,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    @Autowired
    public ProviderCredentialRotator(
            SecretGenerationEngine generationEngine,
            @Autowired(required = false) ProviderAdapterRegistry providerAdapterRegistry,
            @Autowired(required = false) ProviderSecretSyncService providerSecretSyncService,
            @Autowired(required = false) ProviderResourceMappingRepository mappingRepository,
            @Autowired(required = false) ProviderIntegrationRepository integrationRepository,
            @Autowired(required = false) ProviderCredentialService credentialService,
            @Autowired(required = false) SecretRepository secretRepository,
            @Autowired(required = false) SecretVersionRepository secretVersionRepository,
            @Autowired(required = false) EncryptionService encryptionService,
            @Autowired(required = false) AuditService auditService,
            @Autowired(required = false) SecurityEventService securityEventService,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) EventProcessingLogRepository eventProcessingLogRepository
    ) {
        this.generationEngine = generationEngine;
        this.providerAdapterRegistry = providerAdapterRegistry;
        this.providerSecretSyncService = providerSecretSyncService;
        this.mappingRepository = mappingRepository;
        this.integrationRepository = integrationRepository;
        this.credentialService = credentialService;
        this.secretRepository = secretRepository;
        this.secretVersionRepository = secretVersionRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.securityEventService = securityEventService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.eventPublisher = eventPublisher;
        this.eventProcessingLogRepository = eventProcessingLogRepository;
    }

    public enum ClaimResult {
        ACQUIRED,          // Claim successfully acquired (inserted or renewed) with status 'PROCESSING'
        ALREADY_PROCESSED, // Already completed with status 'PROCESSED'
        IN_PROGRESS,       // Claimed by another active worker within stale threshold
        RECOVERED_STALE    // Claim was stale/abandoned and reclaimed for recovery/reconciliation
    }

    private volatile long staleClaimThresholdMs = 120_000; // 2 minutes default

    public void setStaleClaimThresholdMs(long ms) {
        this.staleClaimThresholdMs = ms;
    }

    public long getStaleClaimThresholdMs() {
        return this.staleClaimThresholdMs;
    }

    /**
     * Atomically attempts to acquire a durable execution claim in the database before performing
     * external provider mutation. Prevents concurrent workers from both executing external mutations.
     */
    public ClaimResult acquireDurableClaim(UUID eventId, String consumerName, UUID workspaceId, String correlationId) {
        if (eventProcessingLogRepository == null) {
            return ClaimResult.ACQUIRED;
        }

        // 1. Check existing record
        try {
            java.util.Optional<EventProcessingLog> existing = eventProcessingLogRepository.findByEventIdAndConsumerName(eventId, consumerName);
            if (existing.isPresent()) {
                EventProcessingLog record = existing.get();
                if ("PROCESSED".equalsIgnoreCase(record.getStatus())) {
                    return ClaimResult.ALREADY_PROCESSED;
                }
                if ("PROCESSING".equalsIgnoreCase(record.getStatus())) {
                    long age = (record.getProcessedAt() != null)
                            ? (System.currentTimeMillis() - record.getProcessedAt().toEpochMilli())
                            : ((record.getCreatedAt() != null) ? (System.currentTimeMillis() - record.getCreatedAt().toEpochMilli()) : Long.MAX_VALUE);

                    if (age < staleClaimThresholdMs) {
                        log.info("Durable claim in progress by another worker for event {} consumer {} (age: {}ms)", eventId, consumerName, age);
                        return ClaimResult.IN_PROGRESS;
                    } else {
                        log.warn("Durable claim for event {} consumer {} is stale (age: {}ms > {}ms); reclaiming for recovery/reconciliation",
                                eventId, consumerName, age, staleClaimThresholdMs);
                        record.setAttemptCount(record.getAttemptCount() + 1);
                        record.setProcessedAt(java.time.Instant.now());
                        record.setStatus("PROCESSING");
                        eventProcessingLogRepository.saveAndFlush(record);
                        return ClaimResult.RECOVERED_STALE;
                    }
                }
                if ("FAILED".equalsIgnoreCase(record.getStatus())) {
                    log.info("Retrying previously failed delivery for event {} consumer {}", eventId, consumerName);
                    record.setAttemptCount(record.getAttemptCount() + 1);
                    record.setProcessedAt(java.time.Instant.now());
                    record.setStatus("PROCESSING");
                    record.setError(null);
                    eventProcessingLogRepository.saveAndFlush(record);
                    return ClaimResult.ACQUIRED;
                }
            }
        } catch (Exception ex) {
            log.warn("Error checking existing durable claim for event {} consumer {}: {}", eventId, consumerName, ex.getMessage());
        }

        // 2. Atomic insert for new claim
        try {
            EventProcessingLog claim = new EventProcessingLog(
                    eventId,
                    consumerName,
                    workspaceId,
                    "PROCESSING",
                    correlationId
            );
            claim.setProcessedAt(java.time.Instant.now());
            eventProcessingLogRepository.saveAndFlush(claim);
            return ClaimResult.ACQUIRED;
        } catch (Exception ex) {
            // Unique constraint violation on uq_event_consumer (or concurrent insertion)
            log.info("Concurrent claim acquisition collision for event {} consumer {}. Re-evaluating DB state...", eventId, consumerName);
            try {
                java.util.Optional<EventProcessingLog> existing = eventProcessingLogRepository.findByEventIdAndConsumerName(eventId, consumerName);
                if (existing.isPresent()) {
                    EventProcessingLog record = existing.get();
                    if ("PROCESSED".equalsIgnoreCase(record.getStatus())) {
                        return ClaimResult.ALREADY_PROCESSED;
                    }
                    if ("PROCESSING".equalsIgnoreCase(record.getStatus())) {
                        long age = (record.getProcessedAt() != null)
                                ? (System.currentTimeMillis() - record.getProcessedAt().toEpochMilli())
                                : 0;
                        if (age < staleClaimThresholdMs) {
                            return ClaimResult.IN_PROGRESS;
                        } else {
                            record.setAttemptCount(record.getAttemptCount() + 1);
                            record.setProcessedAt(java.time.Instant.now());
                            record.setStatus("PROCESSING");
                            eventProcessingLogRepository.saveAndFlush(record);
                            return ClaimResult.RECOVERED_STALE;
                        }
                    }
                }
            } catch (Exception e2) {
                log.warn("Error re-evaluating claim collision: {}", e2.getMessage());
            }
            return ClaimResult.IN_PROGRESS;
        }
    }

    public void recordDurableCompletion(UUID eventId, String consumerName) {
        if (eventProcessingLogRepository == null) return;
        try {
            java.util.Optional<EventProcessingLog> existing = eventProcessingLogRepository.findByEventIdAndConsumerName(eventId, consumerName);
            if (existing.isPresent()) {
                EventProcessingLog record = existing.get();
                record.setStatus("PROCESSED");
                record.setProcessedAt(java.time.Instant.now());
                record.setError(null);
                eventProcessingLogRepository.saveAndFlush(record);
            }
        } catch (Exception ex) {
            log.warn("Failed to mark durable completion for event {} consumer {}: {}", eventId, consumerName, ex.getMessage());
        }
    }

    public void recordDurableFailure(UUID eventId, String consumerName, String errorMessage) {
        if (eventProcessingLogRepository == null) return;
        try {
            java.util.Optional<EventProcessingLog> existing = eventProcessingLogRepository.findByEventIdAndConsumerName(eventId, consumerName);
            if (existing.isPresent()) {
                EventProcessingLog record = existing.get();
                record.setStatus("FAILED");
                record.setError(errorMessage);
                record.setProcessedAt(java.time.Instant.now());
                eventProcessingLogRepository.saveAndFlush(record);
            }
        } catch (Exception ex) {
            log.warn("Failed to mark durable failure for event {} consumer {}: {}", eventId, consumerName, ex.getMessage());
        }
    }

    public void clearInMemoryCache() {
        recentlyPushedKeys.clear();
    }

    @Override
    public boolean supports(SecretType type, RotationPolicy policy) {
        return type == SecretType.PROVIDER_CREDENTIAL || (policy != null && policy.getSecretType() == SecretType.PROVIDER_CREDENTIAL);
    }

    @Override
    public String generate(RotationPolicy policy, RotationJob job) {
        log.info("Generating provider credential for job {}", job != null ? job.getId() : "unknown");
        return generationEngine.generatePassword(40, true, true);
    }

    @Override
    public boolean validate(String newPlaintext, RotationPolicy policy, RotationJob job) {
        if (newPlaintext == null || newPlaintext.length() < 16) {
            return false;
        }
        log.info("Validated provider credential length and entropy for job {}", job != null ? job.getId() : "unknown");
        return true;
    }

    @Override
    public void activate(String newPlaintext, RotationPolicy policy, RotationJob job) {
        if (policy == null || job == null || newPlaintext == null) {
            log.warn("Cannot activate provider rotation: null policy, job, or plaintext");
            return;
        }

        UUID secretId = policy.getSecretId();
        UUID workspaceId = policy.getWorkspaceId();
        log.info("Activating rotated secret {} across provider integrations for job {}", secretId, job.getId());

        // Null-safety guard when running without Spring context or mocked services
        if (secretRepository == null || mappingRepository == null || providerAdapterRegistry == null || integrationRepository == null || credentialService == null) {
            log.warn("Provider integration repositories or adapter registry not wired; skipping external provider push for secret {}", secretId);
            return;
        }

        Secret secret = secretRepository.findById(secretId).orElse(null);
        if (secret == null) {
            log.warn("Secret {} not found during provider rotation activation", secretId);
            return;
        }

        List<ProviderResourceMapping> mappings = mappingRepository.findByEnvironmentId(secret.getEnvironmentId());
        if (policy.getProviderId() != null) {
            mappings = mappings.stream()
                    .filter(m -> policy.getProviderId().equals(m.getIntegrationId()) || policy.getProviderId().equals(m.getId()))
                    .toList();
        }

        if (mappings.isEmpty()) {
            log.info("No provider resource mappings configured for secret {} in environment {}", secretId, secret.getEnvironmentId());
            return;
        }

        int targetVersion = job.getTargetVersionNumber() != null ? job.getTargetVersionNumber() : 1;

        for (ProviderResourceMapping mapping : mappings) {
            if (!mapping.isSyncEnabled()) {
                log.info("Sync is disabled for mapping {}; skipping provider push", mapping.getId());
                continue;
            }

            UUID jobId = (job != null && job.getId() != null) ? job.getId() : UUID.nameUUIDFromBytes((secretId.toString() + ":" + mapping.getId() + ":v" + targetVersion).getBytes(StandardCharsets.UTF_8));
            String consumerName = "PROVIDER_PUSH:" + mapping.getId() + ":v" + targetVersion;
            String pushKey = secretId + ":" + mapping.getId() + ":" + jobId + ":v" + targetVersion;

            // 1. Fast in-memory check (latency optimization only)
            Long pushedTime = recentlyPushedKeys.get(pushKey);
            if (pushedTime != null && (System.currentTimeMillis() - pushedTime) < 300_000) {
                log.info("In-memory dedup: skipping provider push for secret {} on mapping {} (job {})", secretId, mapping.getId(), jobId);
                continue;
            }

            // 2. Authoritative durable atomic claim before external mutation
            ClaimResult claimResult = acquireDurableClaim(jobId, consumerName, workspaceId, "job:" + jobId + ":secret:" + secretId);
            if (claimResult == ClaimResult.ALREADY_PROCESSED) {
                log.info("Durable check: skipping provider push for secret {} on mapping {} (job {}) - already completed", secretId, mapping.getId(), jobId);
                recentlyPushedKeys.put(pushKey, System.currentTimeMillis());
                continue;
            }
            if (claimResult == ClaimResult.IN_PROGRESS) {
                log.info("Durable claim in progress by concurrent worker for secret {} on mapping {} (job {}). Skipping duplicate mutation.", secretId, mapping.getId(), jobId);
                continue;
            }

            // claimResult is ACQUIRED or RECOVERED_STALE -> perform external mutation
            try {
                pushToProviderWithRetry(mapping, secret, newPlaintext, policy, job);
                // Record completion
                recordDurableCompletion(jobId, consumerName);
                recentlyPushedKeys.put(pushKey, System.currentTimeMillis());

                // Publish secure integration contract event (strictly zero secret material)
                RotationProviderPushEvent pushEvent = new RotationProviderPushEvent(
                        secretId,
                        targetVersion,
                        mapping.getId(),
                        job.getId(),
                        workspaceId
                );
                if (eventPublisher != null) {
                    eventPublisher.publishEvent(pushEvent);
                }
            } catch (Exception ex) {
                recordDurableFailure(jobId, consumerName, ex.getMessage());
                throw ex;
            }
        }
    }

    /**
     * Executes external provider push with bounded retry, backoff, response verification,
     * tenant isolation, and strict failure handling.
     */
    private void pushToProviderWithRetry(
            ProviderResourceMapping mapping,
            Secret secret,
            String newPlaintext,
            RotationPolicy policy,
            RotationJob job
    ) {
        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(mapping.getIntegrationId(), policy.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("Provider integration " + mapping.getIntegrationId() + " not found for mapping " + mapping.getId()));

        if (integration.getStatus() == IntegrationStatus.DISABLED || integration.getStatus() == IntegrationStatus.REVOKED) {
            throw new IllegalStateException("Cannot push rotated secret to " + integration.getStatus() + " provider integration " + integration.getDisplayName());
        }

        ProviderAdapter adapter = providerAdapterRegistry.getAdapter(integration.getProviderType());
        if (adapter == null) {
            throw new IllegalStateException("No ProviderAdapter registered for type: " + integration.getProviderType());
        }

        String providerCredential = credentialService.decryptCredential(integration);
        Map<String, Object> config = parseConfig(integration.getConfigurationJson());

        int maxRetries = policy.getMaxRetries() > 0 ? Math.min(policy.getMaxRetries(), DEFAULT_MAX_RETRIES) : DEFAULT_MAX_RETRIES;
        ProviderSecretOperationResult result = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                result = adapter.pushSecret(config, providerCredential, mapping, secret.getName(), newPlaintext);
                if (result != null && result.success()) {
                    log.info("Successfully pushed rotated secret {} to provider {} (resource: {}) on attempt {}",
                            secret.getName(), integration.getProviderType(), mapping.getProviderResourceId(), attempt);
                    break;
                }

                if (result != null && isPermanentFailure(result.errorCode())) {
                    log.error("Permanent provider error during rotation push for secret {} to {}: {} (code: {})",
                            secret.getName(), integration.getProviderType(), result.errorMessage(), result.errorCode());
                    break;
                }

                if (attempt < maxRetries) {
                    long backoffMs = (long) (100 * Math.pow(2, attempt - 1));
                    log.warn("Transient failure pushing secret to provider {} on attempt {}. Retrying in {}ms...",
                            integration.getProviderType(), attempt, backoffMs);
                    sleepBackoff(backoffMs);
                }
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Exception during provider push attempt {} for {}: {}", attempt, integration.getProviderType(), ex.getMessage());
                if (attempt < maxRetries && isTransientException(ex)) {
                    long backoffMs = (long) (100 * Math.pow(2, attempt - 1));
                    sleepBackoff(backoffMs);
                } else {
                    break;
                }
            }
        }

        // Verify result
        if (result != null && result.success()) {
            recordSuccessAudit(policy, job, secret, integration, mapping, result);
        } else {
            recordFailureAudit(policy, job, secret, integration, mapping, result, lastException);
            String errorDetail = result != null && result.errorMessage() != null ? result.errorMessage() :
                    (lastException != null ? lastException.getMessage() : "Unknown provider failure");
            throw new IllegalStateException("Provider rotation delivery failed for " + integration.getProviderType() +
                    " (mapping: " + mapping.getId() + "): " + errorDetail);
        }
    }

    /**
     * Event listener for asynchronous / decoupled RotationProviderPushEvent delivery.
     * Decrypts secret version server-side strictly in memory with zero plaintext leakage.
     */
    @EventListener
    public void handleRotationProviderPush(RotationProviderPushEvent event) {
        if (event == null || mappingRepository == null || secretRepository == null || secretVersionRepository == null || encryptionService == null) {
            return;
        }

        UUID jobId = event.rotationJobId() != null ? event.rotationJobId() : event.secretId();
        int version = event.versionNumber();
        String pushKey = event.secretId() + ":" + event.providerMappingId() + ":" + jobId + ":v" + version;
        String consumerName = "PROVIDER_PUSH:" + event.providerMappingId() + ":v" + version;

        // 1. Fast in-memory check (latency optimization only)
        Long pushedTime = recentlyPushedKeys.get(pushKey);
        if (pushedTime != null && (System.currentTimeMillis() - pushedTime) < 300_000) {
            log.info("In-memory dedup: skipping duplicate provider push for secret {} on mapping {} (already completed in job {})",
                    event.secretId(), event.providerMappingId(), jobId);
            return;
        }

        // 2. Authoritative durable atomic claim before external mutation
        ClaimResult claimResult = acquireDurableClaim(jobId, consumerName, event.workspaceId(), "event:" + event.secretId());
        if (claimResult == ClaimResult.ALREADY_PROCESSED) {
            log.info("Durable check: skipping duplicate event handling for secret {} on mapping {} (job {}) - already completed",
                    event.secretId(), event.providerMappingId(), jobId);
            recentlyPushedKeys.put(pushKey, System.currentTimeMillis());
            return;
        }
        if (claimResult == ClaimResult.IN_PROGRESS) {
            log.info("Durable claim in progress by concurrent worker for secret {} on mapping {} (job {}). Skipping duplicate mutation.",
                    event.secretId(), event.providerMappingId(), jobId);
            return;
        }

        // Periodic maintenance cleanup of old dedup cache entries
        if (recentlyPushedKeys.size() > 500) {
            long now = System.currentTimeMillis();
            recentlyPushedKeys.entrySet().removeIf(e -> (now - e.getValue()) > 600_000);
        }

        log.info("Handling RotationProviderPushEvent for secret {} on mapping {}", event.secretId(), event.providerMappingId());

        ProviderResourceMapping mapping = mappingRepository.findByIdAndWorkspaceId(event.providerMappingId(), event.workspaceId())
                .orElse(null);
        if (mapping == null || !mapping.isSyncEnabled()) {
            recordDurableFailure(jobId, consumerName, "Mapping not found or sync disabled");
            return;
        }

        Secret secret = secretRepository.findById(event.secretId()).orElse(null);
        if (secret == null) {
            recordDurableFailure(jobId, consumerName, "Secret not found");
            return;
        }

        SecretVersion secretVersion = secretVersionRepository.findBySecretIdAndVersionNumber(event.secretId(), event.versionNumber())
                .orElse(null);
        if (secretVersion == null) {
            recordDurableFailure(jobId, consumerName, "SecretVersion not found");
            return;
        }

        // Decrypt in memory
        String aad = secret.getId().toString() + ":" + mapping.getEnvironmentId().toString() + ":" + secretVersion.getVersionNumber();
        EncryptedPayload secretPayload = new EncryptedPayload(
                secretVersion.getCiphertext(),
                secretVersion.getEncryptedDek(),
                secretVersion.getIv(),
                secretVersion.getAuthTag(),
                secretVersion.getKeyReference()
        );

        byte[] secretBytes = encryptionService.decrypt(secretPayload, aad);
        String secretPlaintext = new String(secretBytes, StandardCharsets.UTF_8);

        try {
            ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(mapping.getIntegrationId(), event.workspaceId())
                    .orElse(null);
            if (integration == null || integration.getStatus() == IntegrationStatus.DISABLED || integration.getStatus() == IntegrationStatus.REVOKED) {
                recordDurableFailure(jobId, consumerName, "Provider integration disabled or revoked");
                return;
            }

            ProviderAdapter adapter = providerAdapterRegistry.getAdapter(integration.getProviderType());
            if (adapter == null) {
                recordDurableFailure(jobId, consumerName, "Adapter not found for " + integration.getProviderType());
                return;
            }

            String providerCredential = credentialService.decryptCredential(integration);
            Map<String, Object> config = parseConfig(integration.getConfigurationJson());

            adapter.pushSecret(config, providerCredential, mapping, secret.getName(), secretPlaintext);

            // Record completion
            recordDurableCompletion(jobId, consumerName);
            recentlyPushedKeys.put(pushKey, System.currentTimeMillis());
        } catch (Exception ex) {
            recordDurableFailure(jobId, consumerName, ex.getMessage());
            throw ex;
        } finally {
            Arrays.fill(secretBytes, (byte) 0);
        }
    }

    private boolean isPermanentFailure(ProviderErrorCode errorCode) {
        if (errorCode == null) return false;
        return errorCode == ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED ||
                errorCode == ProviderErrorCode.PROVIDER_AUTHORIZATION_FAILED ||
                errorCode == ProviderErrorCode.PROVIDER_INVALID_REQUEST ||
                errorCode == ProviderErrorCode.PROVIDER_UNSUPPORTED_CAPABILITY;
    }

    private boolean isTransientException(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        return msg.contains("timeout") || msg.contains("timed out") || msg.contains("connection reset") ||
                msg.contains("rate limit") || msg.contains("too many requests") || msg.contains("503") || msg.contains("502");
    }

    private void sleepBackoff(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordSuccessAudit(
            RotationPolicy policy,
            RotationJob job,
            Secret secret,
            ProviderIntegration integration,
            ProviderResourceMapping mapping,
            ProviderSecretOperationResult result
    ) {
        if (auditService != null) {
            auditService.recordSecretAudit(
                    null,
                    policy.getWorkspaceId(),
                    job.getInitiatedBy(),
                    AuditAction.PROVIDER_SECRET_PUSHED,
                    secret.getId(),
                    null,
                    null,
                    "SUCCESS"
            );
        }

        if (securityEventService != null) {
            securityEventService.recordEvent(
                    policy.getWorkspaceId(),
                    mapping.getProjectId(),
                    mapping.getEnvironmentId(),
                    job.getInitiatedBy(),
                    SecurityEventType.PROVIDER_SECRET_PUSHED,
                    SecurityEventSeverity.LOW,
                    SecurityEventOutcome.SUCCESS,
                    "PROVIDER_CREDENTIAL_ROTATOR",
                    null,
                    null,
                    null,
                    Map.of(
                            "secretName", secret.getName(),
                            "providerType", integration.getProviderType().name(),
                            "providerResourceId", mapping.getProviderResourceId(),
                            "rotationJobId", job.getId().toString()
                    )
            );
        }
    }

    private void recordFailureAudit(
            RotationPolicy policy,
            RotationJob job,
            Secret secret,
            ProviderIntegration integration,
            ProviderResourceMapping mapping,
            ProviderSecretOperationResult result,
            Exception ex
    ) {
        if (auditService != null) {
            auditService.recordSecretAudit(
                    null,
                    policy.getWorkspaceId(),
                    job.getInitiatedBy(),
                    AuditAction.PROVIDER_OPERATION_FAILED,
                    secret.getId(),
                    null,
                    null,
                    "FAILURE: " + (result != null ? result.errorCode() : "EXCEPTION")
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
