package com.secretvault.ai.domain.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_rca_reports")
public class AiRcaReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "inquiry_id")
    private UUID inquiryId;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType; // DEPLOYMENT, SYNC_JOB, ROTATION_JOB, INCIDENT

    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;

    @Column(name = "root_cause_summary", nullable = false, length = 512)
    private String rootCauseSummary;

    @Column(name = "detailed_explanation", nullable = false, columnDefinition = "TEXT")
    private String detailedExplanation;

    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore = 0.95;

    @Column(name = "telemetry_evidence_json", nullable = false, columnDefinition = "TEXT")
    private String telemetryEvidenceJson = "[]";

    @Column(name = "remediation_strategy", length = 512)
    private String remediationStrategy;

    @Column(name = "drift_hash_mismatch", nullable = false)
    private boolean driftHashMismatch = false;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AiRcaReport() {
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

    public UUID getInquiryId() {
        return inquiryId;
    }

    public void setInquiryId(UUID inquiryId) {
        this.inquiryId = inquiryId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getRootCauseSummary() {
        return rootCauseSummary;
    }

    public void setRootCauseSummary(String rootCauseSummary) {
        this.rootCauseSummary = rootCauseSummary;
    }

    public String getDetailedExplanation() {
        return detailedExplanation;
    }

    public void setDetailedExplanation(String detailedExplanation) {
        this.detailedExplanation = detailedExplanation;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getTelemetryEvidenceJson() {
        return telemetryEvidenceJson;
    }

    public void setTelemetryEvidenceJson(String telemetryEvidenceJson) {
        this.telemetryEvidenceJson = telemetryEvidenceJson;
    }

    public String getRemediationStrategy() {
        return remediationStrategy;
    }

    public void setRemediationStrategy(String remediationStrategy) {
        this.remediationStrategy = remediationStrategy;
    }

    public boolean isDriftHashMismatch() {
        return driftHashMismatch;
    }

    public void setDriftHashMismatch(boolean driftHashMismatch) {
        this.driftHashMismatch = driftHashMismatch;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
