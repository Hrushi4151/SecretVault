package com.secretvault.rotation.entity;

import com.secretvault.rotation.model.RolloutStrategy;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.rotation.model.ValidationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Defines automated rotation schedule, strategy, validation, rollout, and grace period for a secret.
 */
@Entity
@Table(
        name = "rotation_policies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_rotation_policy_secret", columnNames = {"secret_id"})
        }
)
public class RotationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "secret_id", nullable = false)
    private UUID secretId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 32)
    private RotationStrategy strategy = RotationStrategy.SCHEDULED;

    @Column(name = "interval_seconds", nullable = false)
    private long intervalSeconds = 2592000; // 30 days default

    @Column(name = "min_interval_seconds", nullable = false)
    private long minIntervalSeconds = 3600; // 1 hour min

    @Column(name = "max_secret_age_seconds")
    private Long maxSecretAgeSeconds;

    @Column(name = "rotation_window_seconds", nullable = false)
    private long rotationWindowSeconds = 86400; // 24 hours

    @Column(name = "cron_expression", length = 64)
    private String cronExpression;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "retry_backoff_seconds", nullable = false)
    private int retryBackoffSeconds = 300;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_type", nullable = false, length = 32)
    private ValidationType validationType = ValidationType.AUTHENTICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "rollout_strategy", nullable = false, length = 32)
    private RolloutStrategy rolloutStrategy = RolloutStrategy.STAGED;

    @Enumerated(EnumType.STRING)
    @Column(name = "secret_type", nullable = false, length = 32)
    private SecretType secretType = SecretType.PASSWORD;

    @Column(name = "secret_generator_config", columnDefinition = "jsonb")
    private String secretGeneratorConfig;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "grace_period_seconds", nullable = false)
    private long gracePeriodSeconds = 1800; // 30 minutes

    @Column(name = "auto_revoke_previous", nullable = false)
    private boolean autoRevokePrevious = true;

    @Column(name = "auto_rollback_on_failure", nullable = false)
    private boolean autoRollbackOnFailure = true;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval = false;

    @Column(name = "require_jit_approval", nullable = false)
    private boolean requireJitApproval = false;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = true;

    @Column(name = "next_rotation_due_at")
    private Instant nextRotationDueAt;

    @Column(name = "last_rotated_at")
    private Instant lastRotatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RotationPolicy() {
    }

    public RotationPolicy(UUID workspaceId, UUID secretId, UUID createdBy) {
        this.workspaceId = workspaceId;
        this.secretId = secretId;
        this.createdBy = createdBy;
        this.enabled = true;
        this.strategy = RotationStrategy.SCHEDULED;
        this.intervalSeconds = 2592000;
        this.minIntervalSeconds = 3600;
        this.rotationWindowSeconds = 86400;
        this.timezone = "UTC";
        this.maxRetries = 3;
        this.retryBackoffSeconds = 300;
        this.validationType = ValidationType.AUTHENTICATION;
        this.rolloutStrategy = RolloutStrategy.STAGED;
        this.secretType = SecretType.PASSWORD;
        this.gracePeriodSeconds = 1800;
        this.autoRevokePrevious = true;
        this.autoRollbackOnFailure = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.nextRotationDueAt = Instant.now().plusSeconds(this.intervalSeconds);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getSecretId() {
        return secretId;
    }

    public void setSecretId(UUID secretId) {
        this.secretId = secretId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public RotationStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(RotationStrategy strategy) {
        this.strategy = strategy;
        this.updatedAt = Instant.now();
    }

    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
        this.updatedAt = Instant.now();
    }

    public long getMinIntervalSeconds() {
        return minIntervalSeconds;
    }

    public void setMinIntervalSeconds(long minIntervalSeconds) {
        this.minIntervalSeconds = minIntervalSeconds;
    }

    public Long getMaxSecretAgeSeconds() {
        return maxSecretAgeSeconds;
    }

    public void setMaxSecretAgeSeconds(Long maxSecretAgeSeconds) {
        this.maxSecretAgeSeconds = maxSecretAgeSeconds;
    }

    public long getRotationWindowSeconds() {
        return rotationWindowSeconds;
    }

    public void setRotationWindowSeconds(long rotationWindowSeconds) {
        this.rotationWindowSeconds = rotationWindowSeconds;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getRetryBackoffSeconds() {
        return retryBackoffSeconds;
    }

    public void setRetryBackoffSeconds(int retryBackoffSeconds) {
        this.retryBackoffSeconds = retryBackoffSeconds;
    }

    public ValidationType getValidationType() {
        return validationType;
    }

    public void setValidationType(ValidationType validationType) {
        this.validationType = validationType;
    }

    public RolloutStrategy getRolloutStrategy() {
        return rolloutStrategy;
    }

    public void setRolloutStrategy(RolloutStrategy rolloutStrategy) {
        this.rolloutStrategy = rolloutStrategy;
    }

    public SecretType getSecretType() {
        return secretType;
    }

    public void setSecretType(SecretType secretType) {
        this.secretType = secretType;
    }

    public String getSecretGeneratorConfig() {
        return secretGeneratorConfig;
    }

    public void setSecretGeneratorConfig(String secretGeneratorConfig) {
        this.secretGeneratorConfig = secretGeneratorConfig;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public long getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }

    public void setGracePeriodSeconds(long gracePeriodSeconds) {
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public boolean isAutoRevokePrevious() {
        return autoRevokePrevious;
    }

    public void setAutoRevokePrevious(boolean autoRevokePrevious) {
        this.autoRevokePrevious = autoRevokePrevious;
    }

    public boolean isAutoRollbackOnFailure() {
        return autoRollbackOnFailure;
    }

    public void setAutoRollbackOnFailure(boolean autoRollbackOnFailure) {
        this.autoRollbackOnFailure = autoRollbackOnFailure;
    }

    public boolean isRequireApproval() {
        return requireApproval;
    }

    public void setRequireApproval(boolean requireApproval) {
        this.requireApproval = requireApproval;
    }

    public boolean isRequireJitApproval() {
        return requireJitApproval;
    }

    public void setRequireJitApproval(boolean requireJitApproval) {
        this.requireJitApproval = requireJitApproval;
    }

    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }

    public void setNotificationEnabled(boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }

    public Instant getNextRotationDueAt() {
        return nextRotationDueAt;
    }

    public void setNextRotationDueAt(Instant nextRotationDueAt) {
        this.nextRotationDueAt = nextRotationDueAt;
    }

    public Instant getLastRotatedAt() {
        return lastRotatedAt;
    }

    public void setLastRotatedAt(Instant lastRotatedAt) {
        this.lastRotatedAt = lastRotatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
