package com.secretvault.access.review.dto;

import com.secretvault.access.model.AccessScope;
import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;

import java.time.Instant;
import java.util.UUID;

public record AccessReviewCampaignResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        AccessScope scopeType,
        UUID projectId,
        String projectName,
        UUID environmentId,
        String environmentName,
        CampaignStatus status,
        UUID createdBy,
        String createdByEmail,
        Instant dueDate,
        Instant completedAt,
        int totalItemsCount,
        int decidedItemsCount,
        int progressPercentage,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccessReviewCampaignResponse fromEntity(
            AccessReviewCampaign campaign,
            String projectName,
            String environmentName,
            String creatorEmail
    ) {
        int progress = campaign.getTotalItemsCount() > 0
                ? (int) Math.round(((double) campaign.getDecidedItemsCount() / campaign.getTotalItemsCount()) * 100.0)
                : 0;

        return new AccessReviewCampaignResponse(
                campaign.getId(),
                campaign.getWorkspaceId(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getScopeType(),
                campaign.getProjectId(),
                projectName,
                campaign.getEnvironmentId(),
                environmentName,
                campaign.getStatus(),
                campaign.getCreatedBy(),
                creatorEmail,
                campaign.getDueDate(),
                campaign.getCompletedAt(),
                campaign.getTotalItemsCount(),
                campaign.getDecidedItemsCount(),
                progress,
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
