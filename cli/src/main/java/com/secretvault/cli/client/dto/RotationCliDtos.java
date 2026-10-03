package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RotationCliDtos {

    public record RotationPolicyDto(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            boolean enabled,
            int rotationIntervalDays,
            int minIntervalDays,
            int maxSecretAgeDays,
            int rotationWindowHours,
            String rotationWindowStartTime,
            String rotationWindowTimezone,
            String strategy,
            String rolloutStrategy,
            String validationType,
            String customValidationEndpoint,
            String customRotatorPluginId,
            int gracePeriodMinutes,
            int maxRetries,
            int retryBackoffSeconds,
            boolean autoRevokeOldVersion,
            boolean autoRollbackOnFailure,
            boolean notifyOnSuccess,
            Instant lastRotatedAt,
            Instant nextScheduledRotation,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SaveRotationPolicyRequest(
            boolean enabled,
            int rotationIntervalDays,
            int minIntervalDays,
            int maxSecretAgeDays,
            int rotationWindowHours,
            String rotationWindowStartTime,
            String rotationWindowTimezone,
            String strategy,
            String rolloutStrategy,
            String validationType,
            String customValidationEndpoint,
            String customRotatorPluginId,
            int gracePeriodMinutes,
            int maxRetries,
            boolean autoRevokeOldVersion,
            boolean autoRollbackOnFailure,
            boolean notifyOnSuccess
    ) {}

    public record RotationAttemptDto(
            int attemptNumber,
            String status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {}

    public record RotationJobDto(
            UUID id,
            UUID workspaceId,
            UUID secretId,
            UUID policyId,
            String strategy,
            String rolloutStrategy,
            String status,
            Integer oldVersionNumber,
            Integer targetVersionNumber,
            int retryCount,
            int maxRetries,
            Instant nextRetryAt,
            Instant startedAt,
            Instant stagedAt,
            Instant activatedAt,
            Instant gracePeriodEndsAt,
            Instant completedAt,
            Instant rolledBackAt,
            Instant cancelledAt,
            String errorMessage,
            String auditReason,
            Instant createdAt,
            Instant updatedAt,
            List<RotationAttemptDto> attempts
    ) {}

    public record TriggerRotationRequest(
            String strategy,
            String rolloutStrategy,
            String reason,
            String idempotencyKey
    ) {}

    public record CancelRotationRequest(
            String reason
    ) {}

    public record RollbackRotationRequest(
            Integer targetVersionNumber,
            String reason
    ) {}

    public record ConsumerImpactSummary(
            UUID consumerId,
            String consumerName,
            String consumerType,
            UUID environmentId,
            String environmentName,
            UUID machineIdentityId,
            Integer currentVersion,
            boolean supportsDynamicRefresh,
            boolean requiresRestart,
            Instant lastSeenAt,
            String status
    ) {}

    public record RotationImpactDto(
            UUID secretId,
            String secretName,
            UUID environmentId,
            String environmentName,
            Integer currentVersionNumber,
            int totalConsumers,
            int consumersSupportingAutoRefresh,
            int consumersRequiringRestart,
            int staleConsumers,
            int activeLeaseCount,
            List<ConsumerImpactSummary> consumers,
            List<UUID> affectedMachineIdentities,
            Instant calculatedAt
    ) {}

    public record SecretLeaseDto(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer secretVersionNumber,
            UUID consumerId,
            UUID machineIdentityId,
            String accessScope,
            String status,
            long ttlSeconds,
            long maxLifetimeSeconds,
            int renewalCount,
            Instant issuedAt,
            Instant expiresAt,
            Instant maxExpiresAt,
            Instant lastRenewedAt,
            Instant revokedAt,
            String revocationReason,
            Instant createdAt
    ) {}

    public record CreateLeaseRequest(
            UUID secretId,
            Integer secretVersionNumber,
            UUID consumerId,
            UUID machineIdentityId,
            String accessScope,
            long ttlSeconds,
            long maxLifetimeSeconds
    ) {}

    public record RenewLeaseRequest(
            Long ttlSeconds
    ) {}

    public record RevokeLeaseRequest(
            String reason
    ) {}

    public record SecretConsumerDto(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            String name,
            String type,
            UUID machineIdentityId,
            String status,
            Integer acknowledgedSecretVersion,
            boolean supportsDynamicRefresh,
            String sdkVersion,
            String runtimeEnvironment,
            String hostname,
            Instant lastHeartbeatAt,
            Instant registeredAt,
            Instant createdAt
    ) {}
}
