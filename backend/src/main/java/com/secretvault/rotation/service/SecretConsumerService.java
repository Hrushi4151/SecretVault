package com.secretvault.rotation.service;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.model.ConsumerStatus;
import com.secretvault.rotation.model.ConsumerType;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Workload and Application Consumer Registry tracking runtime instances, SDK heartbeats,
 * active secret versions, and dynamic refresh capabilities.
 */
@Service
public class SecretConsumerService {

    private static final Logger log = LoggerFactory.getLogger(SecretConsumerService.class);

    private final SecretConsumerRepository consumerRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;

    public SecretConsumerService(
            SecretConsumerRepository consumerRepository,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService) {
        this.consumerRepository = consumerRepository;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public SecretConsumerResponse registerConsumer(
            UUID workspaceId, UUID projectId, UUID environmentId, RegisterSecretConsumerRequest req, UUID actorId) {

        verifyAccess(workspaceId, projectId, environmentId, null, actorId, AccessPermission.CONSUMER_MANAGE);

        SecretConsumer consumer = consumerRepository.findByWorkspaceIdAndName(workspaceId, req.name())
                .orElseGet(() -> {
                    SecretConsumer c = new SecretConsumer();
                    c.setId(UUID.randomUUID());
                    c.setWorkspaceId(workspaceId);
                    c.setProjectId(projectId);
                    c.setEnvironmentId(environmentId);
                    c.setName(req.name());
                    return c;
                });

        consumer.setConsumerType(req.consumerType() != null ? req.consumerType() : ConsumerType.APPLICATION);
        consumer.setMachineIdentityId(req.machineIdentityId());
        consumer.setStatus(ConsumerStatus.ACTIVE);
        consumer.setInstanceId(req.instanceId());
        consumer.setHostname(req.hostname());
        consumer.setSdkVersion(req.sdkVersion());
        consumer.setRuntimeFramework(req.runtimeFramework());
        consumer.setSupportsDynamicRefresh(req.supportsDynamicRefresh());
        consumer.setRequiresRestart(req.requiresRestart());
        consumer.setLastHeartbeatAt(Instant.now());

        consumer = consumerRepository.save(consumer);
        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.CONSUMER_REGISTERED, null, null, null, "SUCCESS");

        log.info("Registered secret consumer '{}' (ID: {}) in workspace {}", consumer.getName(), consumer.getId(), workspaceId);
        return SecretConsumerResponse.fromEntity(consumer);
    }

    @Transactional
    public SecretConsumerResponse heartbeat(UUID workspaceId, UUID consumerId, ConsumerHeartbeatRequest req) {
        SecretConsumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> ApiException.notFound("Consumer not found"));

        if (!consumer.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.forbidden("Consumer does not belong to this workspace");
        }

        if (consumer.getStatus() == ConsumerStatus.DISABLED) {
            throw ApiException.forbidden("Consumer is disabled");
        }

        Integer prevVersion = consumer.getCurrentAcknowledgedVersion();
        consumer.setLastHeartbeatAt(Instant.now());
        if (req != null) {
            if (req.currentAcknowledgedVersion() != null) consumer.setCurrentAcknowledgedVersion(req.currentAcknowledgedVersion());
            if (req.sdkVersion() != null) consumer.setSdkVersion(req.sdkVersion());
            if (req.runtimeFramework() != null) consumer.setRuntimeFramework(req.runtimeFramework());
        }
        consumer.setStatus(ConsumerStatus.ACTIVE);
        consumer = consumerRepository.save(consumer);

        if (req != null && req.currentAcknowledgedVersion() != null && !req.currentAcknowledgedVersion().equals(prevVersion)) {
            consumer.setLastRefreshAcknowledgedAt(Instant.now());
            auditService.recordSecretAudit(null, workspaceId, null, AuditAction.CONSUMER_REFRESHED, null, null, null, "Refreshed to version " + req.currentAcknowledgedVersion());
        }

        return SecretConsumerResponse.fromEntity(consumer);
    }

    @Transactional(readOnly = true)
    public SecretConsumerResponse getConsumer(UUID workspaceId, UUID consumerId, UUID actorId) {
        SecretConsumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> ApiException.notFound("Consumer not found"));

        verifyAccess(workspaceId, consumer.getProjectId(), consumer.getEnvironmentId(), null, actorId, AccessPermission.SECRET_READ);
        return SecretConsumerResponse.fromEntity(consumer);
    }

    @Transactional(readOnly = true)
    public Page<SecretConsumerResponse> listConsumers(UUID workspaceId, Pageable pageable, UUID actorId) {
        verifyAccess(workspaceId, null, null, null, actorId, AccessPermission.SECRET_READ);
        return consumerRepository.findByWorkspaceId(workspaceId, pageable).map(SecretConsumerResponse::fromEntity);
    }

    @Transactional
    public void disableConsumer(UUID workspaceId, UUID consumerId, UUID actorId) {
        SecretConsumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> ApiException.notFound("Consumer not found"));

        verifyAccess(workspaceId, consumer.getProjectId(), consumer.getEnvironmentId(), null, actorId, AccessPermission.CONSUMER_MANAGE);

        consumer.setStatus(ConsumerStatus.DISABLED);
        consumerRepository.save(consumer);
        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.CONSUMER_DISABLED, null, null, null, "SUCCESS");
    }

    @Scheduled(fixedDelay = 120000) // Every 2 minutes
    @Transactional
    public void detectStaleConsumers() {
        Instant staleThreshold = Instant.now().minus(Duration.ofHours(24));
        List<SecretConsumer> activeConsumers = consumerRepository.findAll();
        for (SecretConsumer consumer : activeConsumers) {
            if (consumer.getStatus() == ConsumerStatus.ACTIVE && consumer.getLastHeartbeatAt() != null && consumer.getLastHeartbeatAt().isBefore(staleThreshold)) {
                consumer.setStatus(ConsumerStatus.STALE);
                consumerRepository.save(consumer);
                log.debug("Marked consumer '{}' as STALE due to heartbeat inactivity", consumer.getName());
            }
        }
    }

    private void verifyAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, UUID actorId, AccessPermission permission) {
        if (actorId == null) return;
        AccessDecision decision = effectiveAccessService.evaluateAccess(workspaceId, projectId, environmentId, secretId, permission, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing " + permission.getCode());
        }
    }
}
