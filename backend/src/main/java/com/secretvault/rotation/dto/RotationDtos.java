package com.secretvault.rotation.dto;

import com.secretvault.rotation.entity.*;
import com.secretvault.rotation.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RotationDtos {

    // --- Policy DTOs ---

    public record CreateRotationPolicyRequest(
            UUID secretId,
            boolean enabled,
            RotationStrategy strategy,
            SecretType secretType,
            long intervalSeconds,
            long minIntervalSeconds,
            Long maxSecretAgeSeconds,
            long rotationWindowSeconds,
            String cronExpression,
            String timezone,
            int maxRetries,
            int retryBackoffSeconds,
            ValidationType validationType,
            RolloutStrategy rolloutStrategy,
            long gracePeriodSeconds,
            boolean autoRevokePrevious,
            boolean autoRollbackOnFailure,
            boolean requireApproval,
            boolean requireJitApproval,
            String secretGeneratorConfig
    ) {}

    public record UpdateRotationPolicyRequest(
            boolean enabled,
            RotationStrategy strategy,
            SecretType secretType,
            long intervalSeconds,
            long minIntervalSeconds,
            Long maxSecretAgeSeconds,
            long rotationWindowSeconds,
            String cronExpression,
            String timezone,
            int maxRetries,
            int retryBackoffSeconds,
            ValidationType validationType,
            RolloutStrategy rolloutStrategy,
            long gracePeriodSeconds,
            boolean autoRevokePrevious,
            boolean autoRollbackOnFailure,
            boolean requireApproval,
            boolean requireJitApproval,
            String secretGeneratorConfig
    ) {}

    public record RotationPolicyResponse(
            UUID id,
            UUID workspaceId,
            UUID secretId,
            boolean enabled,
            RotationStrategy strategy,
            SecretType secretType,
            long intervalSeconds,
            long minIntervalSeconds,
            Long maxSecretAgeSeconds,
            long rotationWindowSeconds,
            String cronExpression,
            String timezone,
            int maxRetries,
            int retryBackoffSeconds,
            ValidationType validationType,
            RolloutStrategy rolloutStrategy,
            long gracePeriodSeconds,
            boolean autoRevokePrevious,
            boolean autoRollbackOnFailure,
            boolean requireApproval,
            boolean requireJitApproval,
            String secretGeneratorConfig,
            Instant lastRotatedAt,
            Instant nextRotationDueAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static RotationPolicyResponse fromEntity(RotationPolicy p) {
            return new RotationPolicyResponse(
                    p.getId(),
                    p.getWorkspaceId(),
                    p.getSecretId(),
                    p.isEnabled(),
                    p.getStrategy(),
                    p.getSecretType(),
                    p.getIntervalSeconds(),
                    p.getMinIntervalSeconds(),
                    p.getMaxSecretAgeSeconds(),
                    p.getRotationWindowSeconds(),
                    p.getCronExpression(),
                    p.getTimezone(),
                    p.getMaxRetries(),
                    p.getRetryBackoffSeconds(),
                    p.getValidationType(),
                    p.getRolloutStrategy(),
                    p.getGracePeriodSeconds(),
                    p.isAutoRevokePrevious(),
                    p.isAutoRollbackOnFailure(),
                    p.isRequireApproval(),
                    p.isRequireJitApproval(),
                    p.getSecretGeneratorConfig(),
                    p.getLastRotatedAt(),
                    p.getNextRotationDueAt(),
                    p.getCreatedAt(),
                    p.getUpdatedAt()
            );
        }
    }

    // --- Job & Execution DTOs ---

    public record TriggerRotationRequest(
            RotationStrategy strategy,
            String reason,
            boolean forceImmediate,
            boolean emergency
    ) {}

    public record RotationRollbackRequest(
            Integer targetVersionNumber,
            String reason
    ) {}

    public record MarkCompromisedRequest(
            String incidentDetails,
            boolean rotateImmediately,
            boolean revokeLeasesImmediately
    ) {}

    public record RotationJobResponse(
            UUID id,
            UUID workspaceId,
            UUID secretId,
            UUID policyId,
            RotationStatus status,
            RotationStrategy triggerType,
            int retryCount,
            int maxRetries,
            Integer targetVersionNumber,
            Integer previousVersionNumber,
            UUID initiatedBy,
            String errorMessage,
            String emergencyReason,
            Instant startedAt,
            Instant stagedAt,
            Instant activatedAt,
            Instant gracePeriodEndsAt,
            Instant completedAt,
            Instant cancelledAt,
            Instant rolledBackAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static RotationJobResponse fromEntity(RotationJob j) {
            return new RotationJobResponse(
                    j.getId(),
                    j.getWorkspaceId(),
                    j.getSecretId(),
                    j.getPolicyId(),
                    j.getStatus(),
                    j.getTriggerType(),
                    j.getRetryCount(),
                    j.getMaxRetries(),
                    j.getTargetVersionNumber(),
                    j.getPreviousVersionNumber(),
                    j.getInitiatedBy(),
                    j.getErrorMessage(),
                    j.getEmergencyReason(),
                    j.getStartedAt(),
                    j.getStagedAt(),
                    j.getActivatedAt(),
                    j.getGracePeriodEndsAt(),
                    j.getCompletedAt(),
                    j.getCancelledAt(),
                    j.getRolledBackAt(),
                    j.getCreatedAt(),
                    j.getUpdatedAt()
            );
        }
    }

    // --- Impact Analysis DTOs ---

    public record ConsumerImpactDetail(
            UUID consumerId,
            String name,
            ConsumerType type,
            UUID environmentId,
            String environmentName,
            UUID machineIdentityId,
            Integer currentVersion,
            boolean dynamicRefreshCapable,
            boolean requiresRestart,
            ConsumerStatus status,
            Instant lastSeenAt
    ) {}

    public record RotationImpactResponse(
            UUID secretId,
            String secretName,
            Integer currentActiveVersion,
            int totalAffectedConsumers,
            int dynamicRefreshCount,
            int restartRequiredCount,
            int activeLeasesCount,
            List<String> affectedEnvironments,
            List<ConsumerImpactDetail> consumers,
            List<String> providerIntegrations,
            Instant computedAt
    ) {}

    // --- Lease DTOs ---

    public record CreateSecretLeaseRequest(
            UUID secretId,
            UUID machineIdentityId,
            UUID consumerId,
            long ttlSeconds,
            long maxLifetimeSeconds,
            String ipAddress,
            String userAgent
    ) {}

    public record RenewSecretLeaseRequest(
            long extendSeconds
    ) {}

    public record SecretLeaseResponse(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID machineIdentityId,
            UUID userId,
            UUID consumerId,
            Integer secretVersionNumber,
            LeaseStatus status,
            long ttlSeconds,
            long maxLifetimeSeconds,
            Instant issuedAt,
            Instant expiresAt,
            Instant lastRenewedAt,
            Instant revokedAt,
            String ipAddress,
            String userAgent,
            Instant createdAt
    ) {
        public static SecretLeaseResponse fromEntity(SecretLease l) {
            return new SecretLeaseResponse(
                    l.getId(),
                    l.getWorkspaceId(),
                    l.getProjectId(),
                    l.getEnvironmentId(),
                    l.getSecretId(),
                    l.getMachineIdentityId(),
                    l.getUserId(),
                    l.getConsumerId(),
                    l.getSecretVersionNumber(),
                    l.getStatus(),
                    l.getTtlSeconds(),
                    l.getMaxLifetimeSeconds(),
                    l.getIssuedAt(),
                    l.getExpiresAt(),
                    l.getLastRenewedAt(),
                    l.getRevokedAt(),
                    l.getIpAddress(),
                    l.getUserAgent(),
                    l.getCreatedAt()
            );
        }
    }

    // --- Consumer DTOs ---

    public record RegisterSecretConsumerRequest(
            String name,
            ConsumerType consumerType,
            UUID machineIdentityId,
            String instanceId,
            String hostname,
            String sdkVersion,
            String runtimeFramework,
            boolean supportsDynamicRefresh,
            boolean requiresRestart
    ) {}

    public record ConsumerHeartbeatRequest(
            Integer currentAcknowledgedVersion,
            String sdkVersion,
            String runtimeFramework
    ) {}

    public record SecretConsumerResponse(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            String name,
            ConsumerType consumerType,
            UUID machineIdentityId,
            ConsumerStatus status,
            Integer currentAcknowledgedVersion,
            String instanceId,
            String hostname,
            String sdkVersion,
            String runtimeFramework,
            boolean supportsDynamicRefresh,
            boolean requiresRestart,
            Instant lastHeartbeatAt,
            Instant createdAt
    ) {
        public static SecretConsumerResponse fromEntity(SecretConsumer c) {
            return new SecretConsumerResponse(
                    c.getId(),
                    c.getWorkspaceId(),
                    c.getProjectId(),
                    c.getEnvironmentId(),
                    c.getName(),
                    c.getConsumerType(),
                    c.getMachineIdentityId(),
                    c.getStatus(),
                    c.getCurrentAcknowledgedVersion(),
                    c.getInstanceId(),
                    c.getHostname(),
                    c.getSdkVersion(),
                    c.getRuntimeFramework(),
                    c.isSupportsDynamicRefresh(),
                    c.isRequiresRestart(),
                    c.getLastHeartbeatAt(),
                    c.getCreatedAt()
            );
        }
    }
}
