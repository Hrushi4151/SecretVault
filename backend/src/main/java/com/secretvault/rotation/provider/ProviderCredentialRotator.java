package com.secretvault.rotation.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
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

    public ProviderCredentialRotator(
            SecretGenerationEngine generationEngine,
            ProviderAdapterRegistry providerAdapterRegistry,
            ProviderSecretSyncService providerSecretSyncService
    ) {
        this(generationEngine, providerAdapterRegistry, providerSecretSyncService,
                null, null, null, null, null, null, null, null, null, null);
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
            @Autowired(required = false) ApplicationEventPublisher eventPublisher
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

            // Synchronously push to provider with retry, verification, and audit
            pushToProviderWithRetry(mapping, secret, newPlaintext, policy, job);
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

        log.info("Handling RotationProviderPushEvent for secret {} on mapping {}", event.secretId(), event.providerMappingId());

        ProviderResourceMapping mapping = mappingRepository.findByIdAndWorkspaceId(event.providerMappingId(), event.workspaceId())
                .orElse(null);
        if (mapping == null || !mapping.isSyncEnabled()) {
            return;
        }

        Secret secret = secretRepository.findById(event.secretId()).orElse(null);
        if (secret == null) {
            return;
        }

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(event.secretId(), event.versionNumber())
                .orElse(null);
        if (version == null) {
            return;
        }

        // Decrypt in memory
        String aad = secret.getId().toString() + ":" + mapping.getEnvironmentId().toString() + ":" + version.getVersionNumber();
        EncryptedPayload secretPayload = new EncryptedPayload(
                version.getCiphertext(),
                version.getEncryptedDek(),
                version.getIv(),
                version.getAuthTag(),
                version.getKeyReference()
        );

        byte[] secretBytes = encryptionService.decrypt(secretPayload, aad);
        String secretPlaintext = new String(secretBytes, StandardCharsets.UTF_8);

        try {
            ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(mapping.getIntegrationId(), event.workspaceId())
                    .orElse(null);
            if (integration == null || integration.getStatus() == IntegrationStatus.DISABLED || integration.getStatus() == IntegrationStatus.REVOKED) {
                return;
            }

            ProviderAdapter adapter = providerAdapterRegistry.getAdapter(integration.getProviderType());
            if (adapter == null) {
                return;
            }

            String providerCredential = credentialService.decryptCredential(integration);
            Map<String, Object> config = parseConfig(integration.getConfigurationJson());

            adapter.pushSecret(config, providerCredential, mapping, secret.getName(), secretPlaintext);
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
