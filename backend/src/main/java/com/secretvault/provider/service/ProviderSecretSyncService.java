package com.secretvault.provider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.dto.PushSecretToProviderResponse;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderSecretMetadata;
import com.secretvault.provider.model.ProviderSecretOperationResult;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Service orchestrating secure in-memory secret synchronization between SecretVault and external providers.
 * Enforces zero-leakage in-memory decryption and strict permission checks.
 */
@Service
public class ProviderSecretSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProviderSecretSyncService.class);

    private final ProviderIntegrationRepository integrationRepository;
    private final ProviderResourceMappingRepository mappingRepository;
    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final EncryptionService encryptionService;
    private final ProviderCredentialService credentialService;
    private final ProviderAdapterRegistry adapterRegistry;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;

    public ProviderSecretSyncService(
            ProviderIntegrationRepository integrationRepository,
            ProviderResourceMappingRepository mappingRepository,
            SecretRepository secretRepository,
            SecretVersionRepository secretVersionRepository,
            EncryptionService encryptionService,
            ProviderCredentialService credentialService,
            ProviderAdapterRegistry adapterRegistry,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            SecurityEventService securityEventService,
            ObjectMapper objectMapper
    ) {
        this.integrationRepository = Objects.requireNonNull(integrationRepository, "integrationRepository must not be null");
        this.mappingRepository = Objects.requireNonNull(mappingRepository, "mappingRepository must not be null");
        this.secretRepository = Objects.requireNonNull(secretRepository, "secretRepository must not be null");
        this.secretVersionRepository = Objects.requireNonNull(secretVersionRepository, "secretVersionRepository must not be null");
        this.encryptionService = Objects.requireNonNull(encryptionService, "encryptionService must not be null");
        this.credentialService = Objects.requireNonNull(credentialService, "credentialService must not be null");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry must not be null");
        this.effectiveAccessService = Objects.requireNonNull(effectiveAccessService, "effectiveAccessService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.securityEventService = Objects.requireNonNull(securityEventService, "securityEventService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Decrypts a SecretVault secret in memory and pushes it directly to the mapped external provider environment.
     */
    @Transactional(readOnly = true)
    public PushSecretToProviderResponse pushSecretToProvider(
            UUID workspaceId,
            UUID integrationId,
            UUID mappingId,
            UUID secretId,
            UUID callerUserId
    ) {
        // 1. Resolve Mapping & Integration
        ProviderResourceMapping mapping = mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider resource mapping not found in this workspace"));

        if (!mapping.getIntegrationId().equals(integrationId)) {
            throw ApiException.notFound("Mapping does not belong to the specified integration");
        }

        if (!mapping.isSyncEnabled()) {
            throw ApiException.badRequest("Synchronization is currently disabled for this resource mapping");
        }

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        if (integration.getStatus() == IntegrationStatus.DISABLED || integration.getStatus() == IntegrationStatus.REVOKED) {
            throw ApiException.badRequest("Cannot synchronize secrets to a " + integration.getStatus() + " integration");
        }

        // 2. Authorize caller for secret sync on this environment/secret
        effectiveAccessService.checkPermission(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(), secretId,
                AccessPermission.INTEGRATION_SYNC, callerUserId
        );

        // 3. Resolve Secret & Version
        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, mapping.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Secret not found in the mapped environment"));

        if (secret.getStatus() == SecretStatus.DELETED || secret.getStatus() == SecretStatus.DISABLED) {
            throw ApiException.badRequest("Cannot push a " + secret.getStatus() + " secret to external provider");
        }

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, secret.getCurrentVersionNumber())
                .orElseThrow(() -> ApiException.notFound("Active secret version not found"));

        // 4. Decrypt secret value in memory
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

        // 5. Decrypt provider credential & push via Adapter
        String providerCredential = credentialService.decryptCredential(integration);
        ProviderAdapter adapter = adapterRegistry.getAdapter(integration.getProviderType());
        Map<String, Object> config = parseConfig(integration.getConfigurationJson());

        ProviderSecretOperationResult opResult;
        try {
            opResult = adapter.pushSecret(config, providerCredential, mapping, secret.getKey(), secretPlaintext);
        } finally {
            // Zeroize in-memory decrypted secret bytes immediately
            Arrays.fill(secretBytes, (byte) 0);
        }

        // 6. Audit Logging & Security Telemetry
        if (opResult.success()) {
            auditService.logAction(
                    workspaceId,
                    callerUserId,
                    AuditAction.PROVIDER_SECRET_PUSHED,
                    "SECRET",
                    secret.getId(),
                    "SUCCESS",
                    Map.of(
                            "secretKey", secret.getKey(),
                            "providerType", integration.getProviderType().name(),
                            "providerResourceId", mapping.getProviderResourceId(),
                            "providerEnvironment", mapping.getProviderEnvironment(),
                            "operation", opResult.operation()
                    )
            );

            securityEventService.recordEvent(
                    workspaceId,
                    mapping.getProjectId(),
                    mapping.getEnvironmentId(),
                    callerUserId,
                    SecurityEventType.PROVIDER_SECRET_PUSHED,
                    SecurityEventSeverity.LOW,
                    SecurityEventOutcome.SUCCESS,
                    Map.of("secretKey", secret.getKey(), "providerType", integration.getProviderType().name(), "providerResourceId", mapping.getProviderResourceId())
            );

            return PushSecretToProviderResponse.success(
                    secret.getId(),
                    secret.getKey(),
                    integration.getProviderType(),
                    mapping.getProviderResourceId(),
                    mapping.getProviderEnvironment(),
                    opResult.providerSecretId(),
                    opResult.operation()
            );
        } else {
            auditService.logAction(
                    workspaceId,
                    callerUserId,
                    AuditAction.PROVIDER_OPERATION_FAILED,
                    "SECRET",
                    secret.getId(),
                    "FAILURE",
                    Map.of(
                            "secretKey", secret.getKey(),
                            "providerType", integration.getProviderType().name(),
                            "error", opResult.errorMessage() != null ? opResult.errorMessage() : "Push failed"
                    )
            );

            return PushSecretToProviderResponse.failure(
                    secret.getId(),
                    secret.getKey(),
                    integration.getProviderType(),
                    mapping.getProviderResourceId(),
                    mapping.getProviderEnvironment(),
                    opResult.errorCode(),
                    opResult.errorMessage()
            );
        }
    }

    /**
     * Deletes a secret from the external provider.
     */
    @Transactional(readOnly = true)
    public ProviderSecretOperationResult deleteSecretFromProvider(
            UUID workspaceId,
            UUID integrationId,
            UUID mappingId,
            String secretKey,
            UUID callerUserId
    ) {
        ProviderResourceMapping mapping = mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider resource mapping not found in this workspace"));

        if (!mapping.getIntegrationId().equals(integrationId)) {
            throw ApiException.notFound("Mapping does not belong to the specified integration");
        }

        effectiveAccessService.checkPermission(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(), null,
                AccessPermission.INTEGRATION_SYNC, callerUserId
        );

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        String providerCredential = credentialService.decryptCredential(integration);
        ProviderAdapter adapter = adapterRegistry.getAdapter(integration.getProviderType());
        Map<String, Object> config = parseConfig(integration.getConfigurationJson());

        ProviderSecretOperationResult result = adapter.deleteSecret(config, providerCredential, mapping, secretKey);

        auditService.logAction(
                workspaceId,
                callerUserId,
                AuditAction.PROVIDER_SECRET_DELETED,
                "PROVIDER_MAPPING",
                mappingId,
                result.success() ? "SUCCESS" : "FAILURE",
                Map.of("secretKey", secretKey, "providerType", integration.getProviderType().name())
        );

        return result;
    }

    /**
     * Lists secret metadata present on the external provider for this mapping.
     */
    @Transactional(readOnly = true)
    public List<ProviderSecretMetadata> listProviderSecrets(
            UUID workspaceId,
            UUID integrationId,
            UUID mappingId,
            UUID callerUserId
    ) {
        ProviderResourceMapping mapping = mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider resource mapping not found in this workspace"));

        if (!mapping.getIntegrationId().equals(integrationId)) {
            throw ApiException.notFound("Mapping does not belong to the specified integration");
        }

        effectiveAccessService.checkPermission(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(), null,
                AccessPermission.INTEGRATION_VIEW, callerUserId
        );

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        String providerCredential = credentialService.decryptCredential(integration);
        ProviderAdapter adapter = adapterRegistry.getAdapter(integration.getProviderType());
        Map<String, Object> config = parseConfig(integration.getConfigurationJson());

        return adapter.listSecrets(config, providerCredential, mapping);
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
