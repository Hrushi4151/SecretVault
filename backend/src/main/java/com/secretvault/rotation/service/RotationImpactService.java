package com.secretvault.rotation.service;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.rotation.dto.RotationDtos.ConsumerImpactDetail;
import com.secretvault.rotation.dto.RotationDtos.RotationImpactResponse;
import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.entity.SecretDependency;
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.repository.SecretDependencyRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service providing real-time dependency analysis and rotation impact calculation across
 * environments, application consumers, machine identities, and cloud providers.
 */
@Service
public class RotationImpactService {

    private static final Logger log = LoggerFactory.getLogger(RotationImpactService.class);

    private final SecretRepository secretRepository;
    private final SecretDependencyRepository dependencyRepository;
    private final SecretConsumerRepository consumerRepository;
    private final SecretLeaseRepository leaseRepository;
    private final EnvironmentRepository environmentRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final ProviderResourceMappingRepository providerMappingRepository;

    public RotationImpactService(
            SecretRepository secretRepository,
            SecretDependencyRepository dependencyRepository,
            SecretConsumerRepository consumerRepository,
            SecretLeaseRepository leaseRepository,
            EnvironmentRepository environmentRepository,
            EffectiveAccessService effectiveAccessService,
            @Autowired(required = false) ProviderResourceMappingRepository providerMappingRepository) {
        this.secretRepository = secretRepository;
        this.dependencyRepository = dependencyRepository;
        this.consumerRepository = consumerRepository;
        this.leaseRepository = leaseRepository;
        this.environmentRepository = environmentRepository;
        this.effectiveAccessService = effectiveAccessService;
        this.providerMappingRepository = providerMappingRepository;
    }

    @Transactional(readOnly = true)
    public RotationImpactResponse calculateImpact(UUID workspaceId, UUID secretId, UUID actorId) {
        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));

        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secretId, actorId, AccessPermission.SECRET_ROTATION_READ);

        // 1. Gather all consumers associated with this secret or its environment
        List<SecretDependency> dependencies = dependencyRepository.findBySecretId(secretId);
        Set<UUID> consumerIds = dependencies.stream()
                .map(SecretDependency::getConsumerId)
                .collect(Collectors.toSet());

        // Also include consumers that hold active leases for this secret
        List<SecretLease> activeLeases = leaseRepository.findBySecretIdAndStatus(secretId, LeaseStatus.ACTIVE);
        for (SecretLease lease : activeLeases) {
            if (lease.getConsumerId() != null) {
                consumerIds.add(lease.getConsumerId());
            }
        }

        // If no explicit direct dependencies recorded yet, pull all consumers in the target environment
        if (consumerIds.isEmpty()) {
            consumerRepository.findByEnvironmentId(secret.getEnvironmentId())
                    .forEach(c -> consumerIds.add(c.getId()));
        }

        List<SecretConsumer> consumers = consumerIds.isEmpty()
                ? Collections.emptyList()
                : consumerRepository.findAllById(consumerIds);

        int dynamicRefreshCount = 0;
        int restartRequiredCount = 0;
        Set<String> affectedEnvs = new HashSet<>();
        List<ConsumerImpactDetail> impactDetails = new ArrayList<>();

        for (SecretConsumer c : consumers) {
            String envName = environmentRepository.findById(c.getEnvironmentId())
                    .map(Environment::getName)
                    .orElse("Unknown");
            affectedEnvs.add(envName);

            boolean dynamic = c.isSupportsDynamicRefresh();
            if (dynamic) {
                dynamicRefreshCount++;
            } else {
                restartRequiredCount++;
            }

            impactDetails.add(new ConsumerImpactDetail(
                    c.getId(),
                    c.getName(),
                    c.getConsumerType(),
                    c.getEnvironmentId(),
                    envName,
                    c.getMachineIdentityId(),
                    c.getCurrentAcknowledgedVersion(),
                    dynamic,
                    c.isRequiresRestart(),
                    c.getStatus(),
                    c.getLastHeartbeatAt()
            ));
        }

        // Add secret's own environment if not already present
        environmentRepository.findById(secret.getEnvironmentId()).ifPresent(e -> affectedEnvs.add(e.getName()));

        // 2. Discover mapped provider platforms
        List<String> providerIntegrations = new ArrayList<>();
        if (providerMappingRepository != null) {
            try {
                providerMappingRepository.findByWorkspaceId(workspaceId).forEach(mapping -> {
                    providerIntegrations.add("Provider Mapping: " + mapping.getProviderResourceName() + " (" + mapping.getProviderEnvironment() + ")");
                });
            } catch (Exception ignored) {
            }
        }

        return new RotationImpactResponse(
                secret.getId(),
                secret.getName(),
                secret.getCurrentVersionNumber(),
                consumers.size(),
                dynamicRefreshCount,
                restartRequiredCount,
                activeLeases.size(),
                new ArrayList<>(affectedEnvs),
                impactDetails,
                providerIntegrations,
                Instant.now()
        );
    }

    private void verifyAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, UUID actorId, AccessPermission permission) {
        if (actorId == null) return;
        AccessDecision decision = effectiveAccessService.evaluateAccess(workspaceId, projectId, environmentId, secretId, permission, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing " + permission.getCode());
        }
    }
}
