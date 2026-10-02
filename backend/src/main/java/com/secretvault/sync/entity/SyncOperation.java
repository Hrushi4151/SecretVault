package com.secretvault.sync.entity;

import com.secretvault.sync.model.SyncOperationStatus;
import com.secretvault.sync.model.SyncOperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record of an individual secret mutation or evaluation within a synchronization job.
 * Never stores or returns secret values or plaintext.
 */
@Entity
@Table(name = "sync_operations")
public class SyncOperation {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "secret_id")
    private UUID secretId;

    @Column(name = "secret_name", nullable = false)
    private String secretName;

    @Column(name = "mapping_id", nullable = false)
    private UUID mappingId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private SyncOperationType operationType = SyncOperationType.NO_OP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SyncOperationStatus status = SyncOperationStatus.PENDING;

    @Column(name = "desired_fingerprint", length = 128)
    private String desiredFingerprint;

    @Column(name = "observed_fingerprint", length = 128)
    private String observedFingerprint;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SyncOperation() {}

    public SyncOperation(
            UUID jobId,
            UUID secretId,
            String secretName,
            UUID mappingId,
            UUID integrationId,
            SyncOperationType operationType,
            String desiredFingerprint,
            String observedFingerprint,
            String reason
    ) {
        this.jobId = jobId;
        this.secretId = secretId;
        this.secretName = secretName;
        this.mappingId = mappingId;
        this.integrationId = integrationId;
        this.operationType = operationType;
        this.desiredFingerprint = desiredFingerprint;
        this.observedFingerprint = observedFingerprint;
        this.reason = reason;
        this.status = SyncOperationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void complete(SyncOperationStatus status, String errorCode, String errorMessage) {
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.executedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public UUID getSecretId() { return secretId; }
    public void setSecretId(UUID secretId) { this.secretId = secretId; }

    public String getSecretName() { return secretName; }
    public void setSecretName(String secretName) { this.secretName = secretName; }

    public UUID getMappingId() { return mappingId; }
    public void setMappingId(UUID mappingId) { this.mappingId = mappingId; }

    public UUID getIntegrationId() { return integrationId; }
    public void setIntegrationId(UUID integrationId) { this.integrationId = integrationId; }

    public SyncOperationType getOperationType() { return operationType; }
    public void setOperationType(SyncOperationType operationType) { this.operationType = operationType; }

    public SyncOperationStatus getStatus() { return status; }
    public void setStatus(SyncOperationStatus status) { this.status = status; }

    public String getDesiredFingerprint() { return desiredFingerprint; }
    public void setDesiredFingerprint(String desiredFingerprint) { this.desiredFingerprint = desiredFingerprint; }

    public String getObservedFingerprint() { return observedFingerprint; }
    public void setObservedFingerprint(String observedFingerprint) { this.observedFingerprint = observedFingerprint; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
