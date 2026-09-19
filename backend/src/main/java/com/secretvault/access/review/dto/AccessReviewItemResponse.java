package com.secretvault.access.review.dto;

import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.review.entity.AccessReviewItem;
import com.secretvault.access.review.entity.ReviewDecision;

import java.time.Instant;
import java.util.UUID;

public record AccessReviewItemResponse(
        UUID id,
        UUID campaignId,
        UUID userId,
        String userEmail,
        String userFullName,
        AccessScope resourceType,
        String resourceName,
        UUID resourceId,
        AccessSourceType sourceType,
        UUID sourceReferenceId,
        String permissionSummary,
        ReviewDecision decision,
        String decisionReason,
        UUID decidedBy,
        String decidedByEmail,
        Instant decidedAt,
        Instant createdAt
) {
    public static AccessReviewItemResponse fromEntity(AccessReviewItem item, String decidedByEmail) {
        return new AccessReviewItemResponse(
                item.getId(),
                item.getCampaignId(),
                item.getUserId(),
                item.getUserEmail(),
                item.getUserFullName(),
                item.getResourceType(),
                item.getResourceName(),
                item.getResourceId(),
                item.getSourceType(),
                item.getSourceReferenceId(),
                item.getPermissionSummary(),
                item.getDecision(),
                item.getDecisionReason(),
                item.getDecidedBy(),
                decidedByEmail,
                item.getDecidedAt(),
                item.getCreatedAt()
        );
    }
}
