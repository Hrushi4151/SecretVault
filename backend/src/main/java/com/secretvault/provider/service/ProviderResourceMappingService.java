package com.secretvault.provider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.provider.dto.CreateResourceMappingRequest;
import com.secretvault.provider.dto.ProviderResourceMappingResponse;
import com.secretvault.provider.dto.UpdateResourceMappingRequest;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProviderResourceMappingService {

    private static final Logger log = LoggerFactory.getLogger(ProviderResourceMappingService.class);

    private final ProviderResourceMappingRepository mappingRepository;
    private final ProviderIntegrationRepository integrationRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;

    public ProviderResourceMappingService(
            ProviderResourceMappingRepository mappingRepository,
            ProviderIntegrationRepository integrationRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            SecurityEventService securityEventService,
            ObjectMapper objectMapper
    ) {
        this.mappingRepository = Objects.requireNonNull(mappingRepository, "mappingRepository must not be null");
        this.integrationRepository = Objects.requireNonNull(integrationRepository, "integrationRepository must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.environmentRepository = Objects.requireNonNull(environmentRepository, "environmentRepository must not be null");
        this.effectiveAccessService = Objects.requireNonNull(effectiveAccessService, "effectiveAccessService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.securityEventService = Objects.requireNonNull(securityEventService, "securityEventService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Creates an explicit mapping between a SecretVault project/environment and an external provider resource.
     */
    @Transactional
    public ProviderResourceMappingResponse createMapping(
            UUID workspaceId,
            UUID integrationId,
            CreateResourceMappingRequest request,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);

        ProviderIntegration integration = integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        projectRepository.findByIdAndWorkspaceId(request.projectId(), workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        environmentRepository.findByIdAndProjectId(request.environmentId(), request.projectId())
                .orElseThrow(() -> ApiException.notFound("Environment not found in this project"));

        if (mappingRepository.existsByWorkspaceIdAndIntegrationIdAndProjectIdAndEnvironmentId(
                workspaceId, integrationId, request.projectId(), request.environmentId()
        )) {
            throw ApiException.conflict("A mapping for this integration, project, and environment already exists");
        }

        String metaJson = "{}";
        if (request.metadata() != null) {
            try {
                metaJson = objectMapper.writeValueAsString(request.metadata());
            } catch (Exception e) {
                log.warn("Failed to serialize mapping metadata JSON: {}", e.getMessage());
            }
        }

        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId,
                integrationId,
                request.projectId(),
                request.environmentId(),
                request.providerResourceType(),
                request.providerResourceId().trim(),
                request.providerResourceName().trim(),
                request.providerEnvironment().trim(),
                metaJson,
                request.syncEnabled() != null ? request.syncEnabled() : true
        );

        ProviderResourceMapping saved = mappingRepository.save(mapping);

        auditService.logAction(
                workspaceId,
                callerUserId,
                AuditAction.PROVIDER_MAPPING_CREATED,
                "PROVIDER_MAPPING",
                saved.getId(),
                "SUCCESS",
                Map.of(
                        "integrationId", integrationId.toString(),
                        "projectId", request.projectId().toString(),
                        "environmentId", request.environmentId().toString(),
                        "providerResourceId", request.providerResourceId()
                )
        );

        securityEventService.recordEvent(
                workspaceId,
                request.projectId(),
                request.environmentId(),
                callerUserId,
                SecurityEventType.PROVIDER_MAPPING_CHANGED,
                SecurityEventSeverity.LOW,
                SecurityEventOutcome.SUCCESS,
                Map.of("action", "CREATE", "providerType", integration.getProviderType().name(), "providerResourceId", request.providerResourceId())
        );

        return ProviderResourceMappingResponse.fromEntity(saved);
    }

    /**
     * Lists all resource mappings for a given integration.
     */
    @Transactional(readOnly = true)
    public List<ProviderResourceMappingResponse> listMappings(UUID workspaceId, UUID integrationId, UUID callerUserId) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_VIEW, callerUserId);

        integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider integration not found in this workspace"));

        return mappingRepository.findByWorkspaceIdAndIntegrationId(workspaceId, integrationId).stream()
                .map(ProviderResourceMappingResponse::fromEntity)
                .toList();
    }

    /**
     * Updates an existing resource mapping.
     */
    @Transactional
    public ProviderResourceMappingResponse updateMapping(
            UUID workspaceId,
            UUID integrationId,
            UUID mappingId,
            UpdateResourceMappingRequest request,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);

        ProviderResourceMapping mapping = mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider resource mapping not found in this workspace"));

        if (!mapping.getIntegrationId().equals(integrationId)) {
            throw ApiException.notFound("Mapping does not belong to the specified integration");
        }

        if (request.providerResourceName() != null && !request.providerResourceName().isBlank()) {
            mapping.setProviderResourceName(request.providerResourceName().trim());
        }

        if (request.providerEnvironment() != null && !request.providerEnvironment().isBlank()) {
            mapping.setProviderEnvironment(request.providerEnvironment().trim());
        }

        if (request.syncEnabled() != null) {
            mapping.setSyncEnabled(request.syncEnabled());
        }

        if (request.metadata() != null) {
            try {
                mapping.setMetadataJson(objectMapper.writeValueAsString(request.metadata()));
            } catch (Exception e) {
                log.warn("Failed to serialize metadata: {}", e.getMessage());
            }
        }

        mapping.setUpdatedAt(Instant.now());
        ProviderResourceMapping updated = mappingRepository.save(mapping);

        auditService.logAction(
                workspaceId,
                callerUserId,
                AuditAction.PROVIDER_MAPPING_UPDATED,
                "PROVIDER_MAPPING",
                mappingId,
                "SUCCESS",
                Map.of("providerEnvironment", updated.getProviderEnvironment(), "syncEnabled", String.valueOf(updated.isSyncEnabled()))
        );

        return ProviderResourceMappingResponse.fromEntity(updated);
    }

    /**
     * Deletes a resource mapping.
     */
    @Transactional
    public void deleteMapping(UUID workspaceId, UUID integrationId, UUID mappingId, UUID callerUserId) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);

        ProviderResourceMapping mapping = mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Provider resource mapping not found in this workspace"));

        if (!mapping.getIntegrationId().equals(integrationId)) {
            throw ApiException.notFound("Mapping does not belong to the specified integration");
        }

        mappingRepository.delete(mapping);

        auditService.logAction(
                workspaceId,
                callerUserId,
                AuditAction.PROVIDER_MAPPING_DELETED,
                "PROVIDER_MAPPING",
                mappingId,
                "SUCCESS",
                Map.of("integrationId", integrationId.toString())
        );
    }
}
