package com.secretvault.security.posture.dto;

import com.secretvault.security.finding.dto.SecurityFindingResponse;
import com.secretvault.security.risk.model.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SecurityOverviewResponse(
        UUID workspaceId,
        int overallRiskScore,
        RiskLevel overallRiskLevel,
        long totalActiveFindings,
        long criticalFindings,
        long highFindings,
        long recentEventsCount,
        long recentDenialsCount,
        String accessReviewStatus,
        List<SecurityFindingResponse> topRiskFindings,
        List<SecurityTimelineEventResponse> recentTimeline,
        Instant generatedAt
) {}
