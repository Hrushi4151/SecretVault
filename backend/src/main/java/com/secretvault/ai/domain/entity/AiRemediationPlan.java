package com.secretvault.ai.domain.entity;

import com.secretvault.ai.domain.model.AiPlanStatus;
import com.secretvault.ai.domain.model.AiRiskLevel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_remediation_plans")
public class AiRemediationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "rca_report_id")
    private UUID rcaReportId;

    @Column(name = "plan_type", nullable = false, length = 64)
    private String planType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 32)
    private AiRiskLevel riskLevel = AiRiskLevel.MEDIUM;

    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore = 0.95;

    @Column(name = "target_resource_type", nullable = false, length = 64)
    private String targetResourceType;

    @Column(name = "target_resource_id", nullable = false, length = 128)
    private String targetResourceId;

    @Column(name = "remediation_steps_json", nullable = false, columnDefinition = "TEXT")
    private String remediationStepsJson = "[]";

    @Column(name = "payload_diff_json", columnDefinition = "TEXT")
    private String payloadDiffJson;

    @Column(name = "blast_radius_json", columnDefinition = "TEXT")
    private String blastRadiusJson;

    @Column(name = "plan_fingerprint", length = 64)
    private String planFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AiPlanStatus status = AiPlanStatus.PENDING_APPROVAL;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "execution_result_json", columnDefinition = "TEXT")
    private String executionResultJson;

    @Column(name = "feedback_rating")
    private Integer feedbackRating;

    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AiRemediationPlan() {
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

    public UUID getRcaReportId() {
        return rcaReportId;
    }

    public void setRcaReportId(UUID rcaReportId) {
        this.rcaReportId = rcaReportId;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AiRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(AiRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getTargetResourceType() {
        return targetResourceType;
    }

    public void setTargetResourceType(String targetResourceType) {
        this.targetResourceType = targetResourceType;
    }

    public String getTargetResourceId() {
        return targetResourceId;
    }

    public void setTargetResourceId(String targetResourceId) {
        this.targetResourceId = targetResourceId;
    }

    public String getRemediationStepsJson() {
        return remediationStepsJson;
    }

    public void setRemediationStepsJson(String remediationStepsJson) {
        this.remediationStepsJson = remediationStepsJson;
    }

    public String getPayloadDiffJson() {
        return payloadDiffJson;
    }

    public void setPayloadDiffJson(String payloadDiffJson) {
        this.payloadDiffJson = payloadDiffJson;
    }

    public String getBlastRadiusJson() {
        return blastRadiusJson;
    }

    public void setBlastRadiusJson(String blastRadiusJson) {
        this.blastRadiusJson = blastRadiusJson;
    }

    public AiPlanStatus getStatus() {
        return status;
    }

    public void setStatus(AiPlanStatus status) {
        this.status = status;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public UUID getReviewedByUserId() {
        return reviewedByUserId;
    }

    public void setReviewedByUserId(UUID reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getExecutionResultJson() {
        return executionResultJson;
    }

    public void setExecutionResultJson(String executionResultJson) {
        this.executionResultJson = executionResultJson;
    }

    public Integer getFeedbackRating() {
        return feedbackRating;
    }

    public void setFeedbackRating(Integer feedbackRating) {
        this.feedbackRating = feedbackRating;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }

    public void setFeedbackComment(String feedbackComment) {
        this.feedbackComment = feedbackComment;
    }

    public String getPlanFingerprint() {
        return planFingerprint;
    }

    public void setPlanFingerprint(String planFingerprint) {
        this.planFingerprint = planFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
