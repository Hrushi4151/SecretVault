package com.secretvault.sync.entity;

import com.secretvault.sync.model.DriftSeverity;
import com.secretvault.sync.model.DriftStatus;
import com.secretvault.sync.model.DriftType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Deduplicated, fingerprinted drift record representing observed state variance
 * between SecretVault and external deployment providers.
 */
@Entity
@Table(
        name = "drift_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_drift_records_ws_fp", columnNames = {"workspace_id", "fingerprint"})
        }
)
public class DriftRecord {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "mapping_id", nullable = false)
    private UUID mappingId;

    @Column(name = "secret_id")
    private UUID secretId;

    @Column(name = "secret_name", nullable = false)
    private String secretName;

    @Column(name = "provider_secret_identifier")
    private String providerSecretIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "drift_type", nullable = false, length = 64)
    private DriftType driftType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private DriftSeverity severity = DriftSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DriftStatus status = DriftStatus.OPEN;

    @Column(name = "desired_fingerprint", length = 128)
    private String desiredFingerprint;

    @Column(name = "observed_fingerprint", length = 128)
    private String observedFingerprint;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "first_detected_at", nullable = false)
    private Instant firstDetectedAt = Instant.now();

    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt = Instant.now();

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount = 1;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_reason", columnDefinition = "TEXT")
    private String resolutionReason;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DriftRecord() {}

    public DriftRecord(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID integrationId,
            UUID mappingId,
            UUID secretId,
            String secretName,
            String providerSecretIdentifier,
            DriftType driftType,
            DriftSeverity severity,
            String desiredFingerprint,
            String observedFingerprint,
            String fingerprint
    ) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.integrationId = integrationId;
        this.mappingId = mappingId;
        this.secretId = secretId;
        this.secretName = secretName;
        this.providerSecretIdentifier = providerSecretIdentifier;
        this.driftType = driftType;
        this.severity = severity;
        this.desiredFingerprint = desiredFingerprint;
        this.observedFingerprint = observedFingerprint;
        this.fingerprint = fingerprint;
        this.status = DriftStatus.OPEN;
        this.firstDetectedAt = Instant.now();
        this.lastDetectedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public DriftRecord(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID integrationId,
            UUID mappingId,
            UUID secretId,
            String secretName,
            String providerSecretIdentifier,
            DriftType driftType,
            DriftSeverity severity,
            String desiredFingerprint,
            String observedFingerprint,
            String fingerprint,
            String errorCode,
            String detailsJson
    ) {
        this(workspaceId, projectId, environmentId, integrationId, mappingId, secretId, secretName, providerSecretIdentifier, driftType, severity, desiredFingerprint, observedFingerprint, fingerprint);
        this.errorCode = errorCode;
        this.detailsJson = detailsJson;
    }

    public void incrementOccurrence(String newObservedFingerprint) {
        this.occurrenceCount++;
        this.lastDetectedAt = Instant.now();
        this.observedFingerprint = newObservedFingerprint;
        this.updatedAt = Instant.now();
        if (this.status == DriftStatus.RESOLVED) {
            this.status = DriftStatus.OPEN; // Re-open drift if re-detected
            this.resolvedAt = null;
            this.resolvedBy = null;
            this.resolutionReason = null;
        }
    }

    public void resolve(UUID resolvedBy, String reason) {
        this.status = DriftStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
        this.resolutionReason = reason;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(UUID environmentId) { this.environmentId = environmentId; }

    public UUID getIntegrationId() { return integrationId; }
    public void setIntegrationId(UUID integrationId) { this.integrationId = integrationId; }

    public UUID getMappingId() { return mappingId; }
    public void setMappingId(UUID mappingId) { this.mappingId = mappingId; }

    public UUID getSecretId() { return secretId; }
    public void setSecretId(UUID secretId) { this.secretId = secretId; }

    public String getSecretName() { return secretName; }
    public void setSecretName(String secretName) { this.secretName = secretName; }

    public String getProviderSecretIdentifier() { return providerSecretIdentifier; }
    public void setProviderSecretIdentifier(String providerSecretIdentifier) { this.providerSecretIdentifier = providerSecretIdentifier; }

    public DriftType getDriftType() { return driftType; }
    public void setDriftType(DriftType driftType) { this.driftType = driftType; }

    public DriftSeverity getSeverity() { return severity; }
    public void setSeverity(DriftSeverity severity) { this.severity = severity; }

    public DriftStatus getStatus() { return status; }
    public void setStatus(DriftStatus status) { this.status = status; }

    public String getDesiredFingerprint() { return desiredFingerprint; }
    public void setDesiredFingerprint(String desiredFingerprint) { this.desiredFingerprint = desiredFingerprint; }

    public String getObservedFingerprint() { return observedFingerprint; }
    public void setObservedFingerprint(String observedFingerprint) { this.observedFingerprint = observedFingerprint; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public void setFirstDetectedAt(Instant firstDetectedAt) { this.firstDetectedAt = firstDetectedAt; }

    public Instant getLastDetectedAt() { return lastDetectedAt; }
    public void setLastDetectedAt(Instant lastDetectedAt) { this.lastDetectedAt = lastDetectedAt; }

    public int getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolutionReason() { return resolutionReason; }
    public void setResolutionReason(String resolutionReason) { this.resolutionReason = resolutionReason; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
