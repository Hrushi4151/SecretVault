package com.secretvault.access.review.dto;

import com.secretvault.access.review.entity.CampaignStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignAttestationReportResponse(
        UUID campaignId,
        UUID workspaceId,
        String campaignName,
        CampaignStatus status,
        Instant certifiedAt,
        String certifiedByEmail,
        int totalItemsReviewed,
        int keptCount,
        int revokedCount,
        List<AccessReviewItemResponse> reviewItems
) {}
