package com.secretvault.rotation.entity;

import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Execution record tracking a discrete secret rotation through its 21-state lifecycle.
 */
@Entity
@Table(name = "rotation_jobs")
public class RotationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "secret_id", nullable = false)
    private UUID secretId;

    @Column(name = "policy_id")
    private UUID policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RotationStatus status = RotationStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private RotationStrategy triggerType = RotationStrategy.SCHEDULED;

    @Column(name = "target_version_number")
    private Integer targetVersionNumber;

    @Column(name = "previous_version_number")
    private Integer previousVersionNumber;

    @Column(name = "generated_version_id")
    private UUID generatedVersionId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "staged_at")
    private Instant stagedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "machine_identity_id")
    private UUID machineIdentityId;

    @Column(name = "emergency_reason", columnDefinition = "text")
    private String emergencyReason;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RotationJob() {
    }

    public RotationJob(UUID workspaceId, UUID secretId, UUID policyId, RotationStrategy triggerType, UUID initiatedBy) {
        this.workspaceId = workspaceId;
        this.secretId = secretId;
        this.policyId = policyId;
        this.triggerType = triggerType;
        this.initiatedBy = initiatedBy;
        this.status = RotationStatus.QUEUED;
        this.retryCount = 0;
        this.maxRetries = 3;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public RotationStatus getStatus() {
        return status;
    }

    public void setStatus(RotationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public RotationStrategy getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(RotationStrategy triggerType) {
        this.triggerType = triggerType;
    }

    public Integer getTargetVersionNumber() {
        return targetVersionNumber;
    }

    public void setTargetVersionNumber(Integer targetVersionNumber) {
        this.targetVersionNumber = targetVersionNumber;
    }

    public Integer getPreviousVersionNumber() {
        return previousVersionNumber;
    }

    public void setPreviousVersionNumber(Integer previousVersionNumber) {
        this.previousVersionNumber = previousVersionNumber;
    }

    public UUID getGeneratedVersionId() {
        return generatedVersionId;
    }

    public void setGeneratedVersionId(UUID generatedVersionId) {
        this.generatedVersionId = generatedVersionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getStagedAt() {
        return stagedAt;
    }

    public void setStagedAt(Instant stagedAt) {
        this.stagedAt = stagedAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Instant getGracePeriodEndsAt() {
        return gracePeriodEndsAt;
    }

    public void setGracePeriodEndsAt(Instant gracePeriodEndsAt) {
        this.gracePeriodEndsAt = gracePeriodEndsAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getRolledBackAt() {
        return rolledBackAt;
    }

    public void setRolledBackAt(Instant rolledBackAt) {
        this.rolledBackAt = rolledBackAt;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(UUID initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public UUID getMachineIdentityId() {
        return machineIdentityId;
    }

    public void setMachineIdentityId(UUID machineIdentityId) {
        this.machineIdentityId = machineIdentityId;
    }

    public String getEmergencyReason() {
        return emergencyReason;
    }

    public void setEmergencyReason(String emergencyReason) {
        this.emergencyReason = emergencyReason;
    }

    public int getVersion() {
        return version;
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
