package com.secretvault.access.review.entity;

import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Access Review Item Entity.
 * Represents a snapshotted grant/role with full lineage for certification by a reviewer.
 */
@Entity
@Table(name = "access_review_items")
public class AccessReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "user_full_name", nullable = false)
    private String userFullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private AccessScope resourceType;

    @Column(name = "resource_name", nullable = false)
    private String resourceName;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private AccessSourceType sourceType;

    @Column(name = "source_reference_id")
    private UUID sourceReferenceId;

    @Column(name = "permission_summary", nullable = false)
    private String permissionSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    private ReviewDecision decision = ReviewDecision.PENDING;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AccessReviewItem() {
    }

    public AccessReviewItem(
            UUID campaignId,
            UUID userId,
            String userEmail,
            String userFullName,
            AccessScope resourceType,
            String resourceName,
            UUID resourceId,
            AccessSourceType sourceType,
            UUID sourceReferenceId,
            String permissionSummary
    ) {
        this.campaignId = Objects.requireNonNull(campaignId, "campaignId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.userEmail = Objects.requireNonNull(userEmail, "userEmail must not be null");
        this.userFullName = Objects.requireNonNull(userFullName, "userFullName must not be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName must not be null");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId must not be null");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
        this.sourceReferenceId = sourceReferenceId;
        this.permissionSummary = Objects.requireNonNull(permissionSummary, "permissionSummary must not be null");
        this.decision = ReviewDecision.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(UUID campaignId) {
        this.campaignId = campaignId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public AccessScope getResourceType() {
        return resourceType;
    }

    public void setResourceType(AccessScope resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public AccessSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(AccessSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getSourceReferenceId() {
        return sourceReferenceId;
    }

    public void setSourceReferenceId(UUID sourceReferenceId) {
        this.sourceReferenceId = sourceReferenceId;
    }

    public String getPermissionSummary() {
        return permissionSummary;
    }

    public void setPermissionSummary(String permissionSummary) {
        this.permissionSummary = permissionSummary;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public void setDecision(ReviewDecision decision) {
        this.decision = decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
