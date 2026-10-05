package com.secretvault.security.finding.entity;

import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
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
 * Deduplicated, fingerprinted security risk and vulnerability finding entity.
 */
@Entity
@Table(
        name = "security_findings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_sec_findings_ws_fp", columnNames = {"workspace_id", "fingerprint"})
        }
)
public class SecurityFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 64)
    private FindingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private FindingSeverity severity = FindingSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 32)
    private FindingConfidence confidence = FindingConfidence.HIGH;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FindingStatus status = FindingStatus.OPEN;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "safe_description", nullable = false, columnDefinition = "TEXT")
    private String safeDescription;

    @Column(name = "remediation_guidance", nullable = false, columnDefinition = "TEXT")
    private String remediationGuidance;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount = 1;

    @Column(name = "first_observed_at", nullable = false)
    private Instant firstObservedAt = Instant.now();

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt = Instant.now();

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_reason", columnDefinition = "TEXT")
    private String resolutionReason;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    public SecurityFinding() {
    }

    public SecurityFinding(UUID workspaceId, String title, FindingSeverity severity, String safeDescription) {
        this(workspaceId, null, null, FindingCategory.SECRET_ROTATION_RISK, severity, FindingConfidence.HIGH, title, safeDescription, "Remediate finding", "{}", "fp-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public SecurityFinding(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            FindingCategory category,
            FindingSeverity severity,
            FindingConfidence confidence,
            String title,
            String safeDescription,
            String remediationGuidance,
            String evidenceJson,
            String fingerprint
    ) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.category = category;
        this.severity = severity != null ? severity : FindingSeverity.MEDIUM;
        this.confidence = confidence != null ? confidence : FindingConfidence.HIGH;
        this.status = FindingStatus.OPEN;
        this.title = title;
        this.safeDescription = safeDescription;
        this.remediationGuidance = remediationGuidance;
        this.evidenceJson = evidenceJson != null ? evidenceJson : "{}";
        this.fingerprint = fingerprint;
        this.occurrenceCount = 1;
        this.firstObservedAt = Instant.now();
        this.lastObservedAt = Instant.now();
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

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public FindingCategory getCategory() {
        return category;
    }

    public FindingSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(FindingSeverity severity) {
        this.severity = severity;
    }

    public FindingConfidence getConfidence() {
        return confidence;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public void setStatus(FindingStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public String getSafeDescription() {
        return safeDescription;
    }

    public void setSafeDescription(String safeDescription) {
        this.safeDescription = safeDescription;
    }

    public String getRemediationGuidance() {
        return remediationGuidance;
    }

    public void setRemediationGuidance(String remediationGuidance) {
        this.remediationGuidance = remediationGuidance;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public void incrementOccurrenceCount() {
        this.occurrenceCount++;
    }

    public Instant getFirstObservedAt() {
        return firstObservedAt;
    }

    public Instant getLastObservedAt() {
        return lastObservedAt;
    }

    public void setLastObservedAt(Instant lastObservedAt) {
        this.lastObservedAt = lastObservedAt;
    }

    public UUID getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(UUID assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
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

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public void setResolutionReason(String resolutionReason) {
        this.resolutionReason = resolutionReason;
    }

    public UUID getResolvedByUserId() {
        return resolvedByUserId;
    }

    public void setResolvedByUserId(UUID resolvedByUserId) {
        this.resolvedByUserId = resolvedByUserId;
    }
}
