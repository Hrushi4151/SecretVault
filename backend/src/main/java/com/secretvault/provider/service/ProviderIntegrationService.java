package com.secretvault.provider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.dto.CreateProviderIntegrationRequest;
import com.secretvault.provider.dto.ProviderIntegrationResponse;
import com.secretvault.provider.dto.UpdateProviderIntegrationRequest;
import com.secretvault.provider.dto.ValidateIntegrationResponse;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderCapability;
import com.secretvault.provider.model.ProviderDiscoveredEnvironment;
import com.secretvault.provider.model.ProviderDiscoveredResource;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.model.ProviderValidationResult;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.util.PaginationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ProviderIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ProviderIntegrationService.class);
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "displayName", "status", "providerType", "lastValidatedAt"
    );

    private final ProviderIntegrationRepository integrationRepository;
    private final ProviderResourceMappingRepository mappingRepository;
    private final ProviderAdapterRegistry adapterRegistry;
    private final ProviderCredentialService credentialService;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;

    public ProviderIntegrationService(
            ProviderIntegrationRepository integrationRepository,
            ProviderResourceMappingRepository mappingRepository,
            ProviderAdapterRegistry adapterRegistry,
            ProviderCredentialService credentialService,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            SecurityEventService securityEventService,
            ObjectMapper objectMapper
    ) {
        this.integrationRepository = Objects.requireNonNull(integrationRepository, "integrationRepository must not be null");
        this.mappingRepository = Objects.requireNonNull(mappingRepository, "mappingRepository must not be null");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry must not be null");
        this.credentialService = Objects.requireNonNull(credentialService, "credentialService must not be null");
        this.effectiveAccessService = Objects.requireNonNull(effectiveAccessService, "effectiveAccessService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.securityEventService = Objects.requireNonNull(securityEventService, "securityEventService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Creates a new external platform provider integration.
     * Validates connection credentials immediately, envelope-encrypts them, and persists the integration.
     */
    @Transactional
    public ProviderIntegrationResponse createIntegration(
            UUID workspaceId,
            CreateProviderIntegrationRequest request,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);

        if (integrationRepository.existsByWorkspaceIdAndDisplayName(workspaceId, request.displayName().trim())) {
            throw ApiException.conflict("A provider integration with display name '" + request.displayName() + "' already exists in this workspace");
        }

        ProviderAdapter adapter = adapterRegistry.getAdapter(request.providerType());
        Map<String, Object> config = request.configuration() != null ? request.configuration() : Collections.emptyMap();

        // 1. Immediate connection validation
        ProviderValidationResult validation = adapter.validateConnection(config, request.credential().trim());
        IntegrationStatus status = validation.valid() ? IntegrationStatus.ACTIVE : IntegrationStatus.ERROR;

        ProviderIntegration integration = new ProviderIntegration();
        integration.setId(UUID.randomUUID());
        integration.setWorkspaceId(workspaceId);
        integration.setProviderType(request.providerType());
        integration.setDisplayName(request.displayName().trim());
        integration.setStatus(status);
        integration.setCreatedBy(callerUserId);

        if (validation.valid()) {
            integration.setLastValidatedAt(Instant.now());
        } else {
            integration.setLastErrorAt(Instant.now());
            integration.setLastErrorCode(validation.errorCode() != null ? validation.errorCode().name() : "VALIDATION_FAILED");
            integration.setLastErrorMessage(validation.errorMessage());
        }

        try {
            integration.setConfigurationJson(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            integration.setConfigurationJson("{}");
        }

        // 2. Envelope encryption with AAD context binding
        credentialService.encryptAndSetCredentials(integration, request.credential().trim());

        ProviderIntegration saved = integrationRepository.save(integration);

        // 3. Auditing & Security Telemetry
        auditService.logAction(
                workspaceId,
                callerUserId,
                AuditAction.PROVIDER_INTEGRATION_CREATED,
                "PROVIDER_INTEGRATION",
                saved.getId(),
                validation.valid() ? "SUCCESS" : "FAILURE",
                Map.of("providerType", saved.getProviderType().name(), "displayName", saved.getDisplayName(), "status", saved.getStatus().name())
        );

        if (!validation.valid()) {
            securityEventService.recordEvent(
                    workspaceId,
                    null,
                    null,
                    callerUserId,
                    SecurityEventType.PROVIDER_CREDENTIAL_VALIDATION_FAILURE,
                    SecurityEventSeverity.MEDIUM,
                    SecurityEventOutcome.FAILURE,
                    Map.of("providerType", saved.getProviderType().name(), "displayName", saved.getDisplayName(), "error", validation.errorMessage() != null ? validation.errorMessage() : "Validation failed")
            );
        }

        return ProviderIntegrationResponse.fromEntity(saved);
    }

    /**
     * Updates an existing integration. If new credentials are provided, validates them first before replacing.
     */
    @Transactional
    public ProviderIntegrationResponse updateIntegration(
            UUID workspaceId,
            UUID integrationId,
            UpdateProviderIntegrationRequest request,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            String newName = request.displayName().trim();
            if (!newName.equalsIgnoreCase(integration.getDisplayName()) &&
                    integrationRepository.existsByWorkspaceIdAndDisplayName(workspaceId, newName)) {
                throw ApiException.conflict("A provider integration with display name '" + newName + "' already exists in this workspace");
            }
            integration.setDisplayName(newName);
        }

        if (request.configuration() != null) {
            try {
                integration.setConfigurationJson(objectMapper.writeValueAsString(request.configuration()));
            } catch (Exception e) {
                log.warn("Failed to serialize configuration JSON: {}", e.getMessage());
            }
        }

        if (request.status() != null) {
            integration.setStatus(request.status());
        }

        // Atomic credential rotation
        if (request.newCredential() != null && !request.newCredential().isBlank()) {
            ProviderAdapter adapter = adapterRegistry.getAdapter(integration.getProviderType());
            Map<String, Object> config = parseConfig(integration.getConfigurationJson());

            ProviderValidationResult validation = adapter.validateConnection(config, request.newCredential().trim());
            if (!validation.valid()) {
                throw ApiException.badRequest("New credential validation failed: " + validation.errorMessage());
            }

            credentialService.encryptAndSetCredentials(integration, request.newCredential().trim());
            integration.setLastValidatedAt(Instant.now());
            integration.setStatus(IntegrationStatus.ACTIVE);
            integration.setLastErrorAt(null);
            integration.setLastErrorCode(null);
            integration.setLastErrorMessage(null);
        }

        integration.setUpdatedAt(Instant.now());
        ProviderIntegration updated = integrationRepository.save(integration);

        auditService.logAction(
                workspaceId,
                callerUserId,
                AuditAction.PROVIDER_INTEGRATION_UPDATED,
                "PROVIDER_INTEGRATION",
                updated.getId(),
                "SUCCESS",
                Map.of("displayName", updated.getDisplayName(), "status", updated.getStatus().name())
        );

        return ProviderIntegrationResponse.fromEntity(updated);
    }

    /**
     * Validates connection credentials against the live external provider.
     */
    @Transactional
    public ValidateIntegrationResponse validateIntegration(
            UUID workspaceId,
            UUID integrationId,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        ProviderAdapter adapter = adapterRegistry.getAdapter(integration.getProviderType());
        Map<String, Object> config = parseConfig(integration.getConfigurationJson());
        String credential = credentialService.decryptCredential(integration);

        ProviderValidationResult result = adapter.validateConnection(config, credential);

        if (result.valid()) {
            integration.setStatus(IntegrationStatus.ACTIVE);
            integration.setLastValidatedAt(Instant.now());
            integration.setLastErrorCode(null);
            integration.setLastErrorMessage(null);
        } else {
            integration.setStatus(IntegrationStatus.ERROR);
            integration.setLastErrorAt(Instant.now());
            integration.setLastErrorCode(result.errorCode() != null ? result.errorCode().name() : "VALIDATION_FAILED");
            integration.setLastErrorMessage(result.errorMessage());

            securityEventService.recordEvent(
                    workspaceId,
                    null,
                    null,
                    callerUserId,
                    SecurityEventType.PROVIDER_CREDENTIAL_VALIDATION_FAILURE,
                    SecurityEventSeverity.MEDIUM,
                    SecurityEventOutcome.FAILURE,
                    Map.of("providerType", integration.getProviderType().name(), "displayName", integration.getDisplayName(), "error", result.errorMessage() != null ? result.errorMessage() : "Validation failed")
            );
        }

        integration.setUpdatedAt(Instant.now());
        integrationRepository.save(integration);

        auditService.logAction(
                workspaceId,
                callerUserId,
                AuditAction.PROVIDER_INTEGRATION_VALIDATED,
                "PROVIDER_INTEGRATION",
                integrationId,
                result.valid() ? "SUCCESS" : "FAILURE",
                Map.of("valid", String.valueOf(result.valid()))
        );

        return ValidateIntegrationResponse.fromResult(result);
    }

    /**
     * Deletes an integration and cascades to associated resource mappings.
     */
    @Transactional
    public void deleteIntegration(UUID workspaceId, UUID integrationId, UUID callerUserId) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        mappingRepository.deleteByIntegrationId(integrationId);
        integrationRepository.delete(integration);

        auditService.logAction(
                workspaceId,
                callerUserId,
                AuditAction.PROVIDER_INTEGRATION_DELETED,
                "PROVIDER_INTEGRATION",
                integrationId,
                "SUCCESS",
                Map.of("displayName", integration.getDisplayName(), "providerType", integration.getProviderType().name())
        );
    }

    /**
     * Retrieves an integration's metadata and redacted credentials.
     */
    @Transactional(readOnly = true)
    public ProviderIntegrationResponse getIntegration(UUID workspaceId, UUID integrationId, UUID callerUserId) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_VIEW, callerUserId);

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        return ProviderIntegrationResponse.fromEntity(integration);
    }

    /**
     * Lists integrations within a workspace with optional filters and whitelisted sorting.
     */
    @Transactional(readOnly = true)
    public Page<ProviderIntegrationResponse> listIntegrations(
            UUID workspaceId,
            ProviderType providerType,
            IntegrationStatus status,
            String search,
            int page,
            int size,
            String sort,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_VIEW, callerUserId);

        Pageable pageable = PaginationUtils.buildPageRequest(page, size, sort, ALLOWED_SORT_FIELDS, "createdAt");
        String cleanSearch = search != null && !search.isBlank() ? search.trim() : null;

        return integrationRepository.searchIntegrations(workspaceId, providerType, status, cleanSearch, pageable)
                .map(ProviderIntegrationResponse::fromEntity);
    }

    /**
     * Retrieves the capabilities supported by this integration's provider adapter.
     */
    @Transactional(readOnly = true)
    public Set<ProviderCapability> getCapabilities(UUID workspaceId, UUID integrationId, UUID callerUserId) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_VIEW, callerUserId);

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        ProviderAdapter adapter = adapterRegistry.getAdapter(integration.getProviderType());
        return adapter.getCapabilities();
    }

    /**
     * Discovers projects/services accessible on the provider account.
     */
    @Transactional(readOnly = true)
    public List<ProviderDiscoveredResource> discoverResources(UUID workspaceId, UUID integrationId, UUID callerUserId) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_VIEW, callerUserId);

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        ProviderAdapter adapter = adapterRegistry.getAdapter(integration.getProviderType());
        Map<String, Object> config = parseConfig(integration.getConfigurationJson());
        String credential = credentialService.decryptCredential(integration);

        return adapter.discoverResources(config, credential);
    }

    /**
     * Discovers environments/tiers available for a specific provider resource.
     */
    @Transactional(readOnly = true)
    public List<ProviderDiscoveredEnvironment> discoverEnvironments(
            UUID workspaceId,
            UUID integrationId,
            String providerResourceId,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_VIEW, callerUserId);

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        ProviderAdapter adapter = adapterRegistry.getAdapter(integration.getProviderType());
        Map<String, Object> config = parseConfig(integration.getConfigurationJson());
        String credential = credentialService.decryptCredential(integration);

        return adapter.discoverEnvironments(config, credential, providerResourceId);
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
