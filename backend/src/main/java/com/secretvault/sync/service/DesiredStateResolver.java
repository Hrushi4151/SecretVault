package com.secretvault.sync.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.sync.model.DesiredSecretState;
import com.secretvault.sync.model.SecretFingerprintCalculator;
import com.secretvault.sync.model.SyncScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Resolves the desired state of SecretVault secrets for target synchronization scopes.
 * Desired state is constructed strictly from authoritative metadata and ciphertext tokens
 * without storing or exposing plaintext values.
 */
@Service
public class DesiredStateResolver {

    private static final Logger log = LoggerFactory.getLogger(DesiredStateResolver.class);

    private final ProviderResourceMappingRepository mappingRepository;
    private final ProviderIntegrationRepository integrationRepository;
    private final SecretRepository secretRepository;
    private final SecretVersionRepository versionRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;

    public DesiredStateResolver(
            ProviderResourceMappingRepository mappingRepository,
            ProviderIntegrationRepository integrationRepository,
            SecretRepository secretRepository,
            SecretVersionRepository versionRepository,
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository
    ) {
        this.mappingRepository = Objects.requireNonNull(mappingRepository, "mappingRepository must not be null");
        this.integrationRepository = Objects.requireNonNull(integrationRepository, "integrationRepository must not be null");
        this.secretRepository = Objects.requireNonNull(secretRepository, "secretRepository must not be null");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository must not be null");
        this.environmentRepository = Objects.requireNonNull(environmentRepository, "environmentRepository must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
    }

    /**
     * Resolves all active provider mappings and their corresponding desired secret states
     * within the specified workspace and synchronization scope.
     */
    @Transactional(readOnly = true)
    public Map<ProviderResourceMapping, List<DesiredSecretState>> resolveDesiredState(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId
    ) {
        if (workspaceId == null) {
            throw ApiException.badRequest("workspaceId must not be null");
        }
        SyncScope effectiveScope = scope != null ? scope : SyncScope.WORKSPACE;

        List<ProviderResourceMapping> targetMappings = resolveTargetMappings(workspaceId, effectiveScope, scopeResourceId);
        Map<ProviderResourceMapping, List<DesiredSecretState>> result = new LinkedHashMap<>();

        for (ProviderResourceMapping mapping : targetMappings) {
            List<DesiredSecretState> desiredList = resolveDesiredStateForMapping(workspaceId, mapping);
            result.put(mapping, desiredList);
        }

        return result;
    }

    /**
     * Resolves desired secret states for a single provider resource mapping.
     */
    @Transactional(readOnly = true)
    public List<DesiredSecretState> resolveDesiredStateForMapping(UUID workspaceId, ProviderResourceMapping mapping) {
        if (mapping == null || !mapping.getWorkspaceId().equals(workspaceId)) {
            return Collections.emptyList();
        }

        UUID environmentId = mapping.getEnvironmentId();
        UUID projectId = mapping.getProjectId();

        List<Secret> secrets = secretRepository.findByEnvironmentIdAndStatus(environmentId, SecretStatus.ACTIVE);
        List<DesiredSecretState> desiredStates = new ArrayList<>();

        for (Secret secret : secrets) {
            Optional<SecretVersion> latestVersionOpt = versionRepository.findTopBySecretIdOrderByVersionNumberDesc(secret.getId());
            if (latestVersionOpt.isEmpty()) {
                continue;
            }
            SecretVersion version = latestVersionOpt.get();

            String desiredFingerprint = SecretFingerprintCalculator.computeMetadataFingerprint(
                    secret.getName(),
                    version.getVersionNumber(),
                    version.getEncryptedDek()
            );

            DesiredSecretState state = new DesiredSecretState(
                    workspaceId,
                    projectId,
                    environmentId,
                    secret.getId(),
                    secret.getName(),
                    version.getVersionNumber(),
                    desiredFingerprint,
                    secret.getStatus() == SecretStatus.ACTIVE,
                    mapping.getId(),
                    secret.getUpdatedAt() != null ? secret.getUpdatedAt() : Instant.now()
            );
            desiredStates.add(state);
        }

        return desiredStates;
    }

    private List<ProviderResourceMapping> resolveTargetMappings(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId
    ) {
        List<ProviderResourceMapping> allWorkspaceMappings = mappingRepository.findByWorkspaceId(workspaceId);

        // Filter for active integrations only
        Map<UUID, ProviderIntegration> activeIntegrations = new HashMap<>();
        List<ProviderIntegration> integrations = integrationRepository.findByWorkspaceId(workspaceId);
        for (ProviderIntegration integration : integrations) {
            if (integration.getStatus() == IntegrationStatus.ACTIVE) {
                activeIntegrations.put(integration.getId(), integration);
            }
        }

        List<ProviderResourceMapping> validMappings = allWorkspaceMappings.stream()
                .filter(m -> activeIntegrations.containsKey(m.getIntegrationId()))
                .toList();

        return switch (scope) {
            case WORKSPACE -> validMappings;
            case PROJECT -> {
                if (scopeResourceId == null) {
                    throw ApiException.badRequest("scopeResourceId required for PROJECT scope");
                }
                yield validMappings.stream()
                        .filter(m -> scopeResourceId.equals(m.getProjectId()))
                        .toList();
            }
            case ENVIRONMENT -> {
                if (scopeResourceId == null) {
                    throw ApiException.badRequest("scopeResourceId required for ENVIRONMENT scope");
                }
                yield validMappings.stream()
                        .filter(m -> scopeResourceId.equals(m.getEnvironmentId()))
                        .toList();
            }
            case MAPPING -> {
                if (scopeResourceId == null) {
                    throw ApiException.badRequest("scopeResourceId required for MAPPING scope");
                }
                yield validMappings.stream()
                        .filter(m -> scopeResourceId.equals(m.getId()))
                        .toList();
            }
            case SECRET -> {
                if (scopeResourceId == null) {
                    throw ApiException.badRequest("scopeResourceId required for SECRET scope");
                }
                Secret secret = secretRepository.findById(scopeResourceId)
                        .orElseThrow(() -> ApiException.notFound("Secret not found"));
                yield validMappings.stream()
                        .filter(m -> secret.getEnvironmentId().equals(m.getEnvironmentId()))
                        .toList();
            }
        };
    }
}
