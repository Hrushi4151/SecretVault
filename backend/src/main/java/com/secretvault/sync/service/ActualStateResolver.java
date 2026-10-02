package com.secretvault.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.*;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.sync.model.ActualStateResult;
import com.secretvault.sync.model.DriftType;
import com.secretvault.sync.model.ProviderSecretState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Connects to external platform providers to inspect remote deployment secret state.
 * Accurately classifies provider unreachable/permission error states vs missing secrets.
 * Zero plaintext values are logged or persisted.
 */
@Service
public class ActualStateResolver {

    private static final Logger log = LoggerFactory.getLogger(ActualStateResolver.class);

    private final ProviderIntegrationRepository integrationRepository;
    private final ProviderCredentialService credentialService;
    private final ProviderAdapterRegistry adapterRegistry;
    private final ObjectMapper objectMapper;

    public ActualStateResolver(
            ProviderIntegrationRepository integrationRepository,
            ProviderCredentialService credentialService,
            ProviderAdapterRegistry adapterRegistry,
            ObjectMapper objectMapper
    ) {
        this.integrationRepository = Objects.requireNonNull(integrationRepository, "integrationRepository must not be null");
        this.credentialService = Objects.requireNonNull(credentialService, "credentialService must not be null");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Resolves observed provider secret states for a specific resource mapping.
     */
    @Transactional(readOnly = true)
    public ActualStateResult resolveActualState(UUID workspaceId, ProviderResourceMapping mapping) {
        if (mapping == null || !mapping.getWorkspaceId().equals(workspaceId)) {
            return ActualStateResult.error(DriftType.RESOURCE_MAPPING_MISMATCH, "INVALID_MAPPING", "Mapping not found or tenant mismatch");
        }

        Optional<ProviderIntegration> integrationOpt = integrationRepository.findByIdAndWorkspaceId(mapping.getIntegrationId(), workspaceId);
        if (integrationOpt.isEmpty()) {
            return ActualStateResult.error(DriftType.RESOURCE_MAPPING_MISMATCH, "INTEGRATION_NOT_FOUND", "Provider integration missing");
        }

        ProviderIntegration integration = integrationOpt.get();
        if (integration.getStatus() != IntegrationStatus.ACTIVE) {
            return ActualStateResult.error(DriftType.PROVIDER_UNAVAILABLE, "INTEGRATION_INACTIVE", "Integration is " + integration.getStatus());
        }

        ProviderAdapter adapter;
        try {
            adapter = adapterRegistry.getAdapter(integration.getProviderType());
        } catch (ApiException e) {
            return ActualStateResult.error(DriftType.UNSUPPORTED, "ADAPTER_NOT_FOUND", e.getMessage());
        }

        if (!adapter.getCapabilities().contains(ProviderCapability.READ_SECRET_METADATA)) {
            return ActualStateResult.error(DriftType.UNSUPPORTED, "CAPABILITY_MISSING", "Provider does not support READ_SECRET_METADATA capability");
        }

        String credential = credentialService.decryptCredential(integration);
        Map<String, Object> config = parseConfig(integration.getConfigurationJson());

        // Validate connection first to distinguish between empty remote env and provider failure
        ProviderValidationResult validation = adapter.validateConnection(config, credential);
        if (!validation.valid()) {
            ProviderErrorCode err = validation.errorCode();
            if (err == ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED || err == ProviderErrorCode.PROVIDER_AUTHORIZATION_FAILED) {
                return ActualStateResult.error(DriftType.PERMISSION_DENIED, err.name(), validation.errorMessage());
            } else if (err == ProviderErrorCode.PROVIDER_RATE_LIMITED || err == ProviderErrorCode.PROVIDER_TIMEOUT || err == ProviderErrorCode.PROVIDER_UNAVAILABLE) {
                return ActualStateResult.error(DriftType.PROVIDER_UNAVAILABLE, err != null ? err.name() : "PROVIDER_UNAVAILABLE", validation.errorMessage());
            } else {
                return ActualStateResult.error(DriftType.PROVIDER_UNAVAILABLE, "VALIDATION_FAILED", validation.errorMessage());
            }
        }

        try {
            List<ProviderSecretMetadata> metadataList = adapter.listSecrets(config, credential, mapping);
            List<ProviderSecretState> states = new ArrayList<>();
            Instant now = Instant.now();

            for (ProviderSecretMetadata meta : metadataList) {
                ProviderSecretState state = new ProviderSecretState(
                        integration.getId(),
                        mapping.getId(),
                        mapping.getProviderResourceId(),
                        mapping.getProviderEnvironment(),
                        meta.key(),
                        meta.providerSecretId() != null ? meta.providerSecretId() : meta.key(),
                        null, // Provider does not expose plaintext fingerprint by default
                        true,
                        meta.targetEnvironment() != null ? meta.targetEnvironment() : mapping.getProviderEnvironment(),
                        meta.updatedAt() != null ? meta.updatedAt() : now
                );
                states.add(state);
            }

            return ActualStateResult.success(states);
        } catch (Exception e) {
            log.warn("Error retrieving provider secrets for mapping [{}]: {}", mapping.getId(), e.getMessage());
            return ActualStateResult.error(DriftType.PROVIDER_UNAVAILABLE, "LIST_FAILED", "Failed to retrieve provider secrets: " + e.getMessage());
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
