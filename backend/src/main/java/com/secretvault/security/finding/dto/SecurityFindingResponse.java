package com.secretvault.security.finding.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public record SecurityFindingResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        FindingCategory category,
        FindingSeverity severity,
        FindingConfidence confidence,
        FindingStatus status,
        String title,
        String safeDescription,
        String remediationGuidance,
        Map<String, Object> evidence,
        String fingerprint,
        int occurrenceCount,
        Instant firstObservedAt,
        Instant lastObservedAt,
        UUID assigneeUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        String resolutionReason,
        UUID resolvedByUserId
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static SecurityFindingResponse fromEntity(SecurityFinding finding) {
        if (finding == null) {
            return null;
        }

        Map<String, Object> evidenceMap = Collections.emptyMap();
        if (finding.getEvidenceJson() != null && !finding.getEvidenceJson().isBlank()) {
            try {
                evidenceMap = MAPPER.readValue(finding.getEvidenceJson(), Map.class);
            } catch (Exception ignored) {
            }
        }

        return new SecurityFindingResponse(
                finding.getId(),
                finding.getWorkspaceId(),
                finding.getProjectId(),
                finding.getEnvironmentId(),
                finding.getCategory(),
                finding.getSeverity(),
                finding.getConfidence(),
                finding.getStatus(),
                finding.getTitle(),
                finding.getSafeDescription(),
                finding.getRemediationGuidance(),
                evidenceMap,
                finding.getFingerprint(),
                finding.getOccurrenceCount(),
                finding.getFirstObservedAt(),
                finding.getLastObservedAt(),
                finding.getAssigneeUserId(),
                finding.getCreatedAt(),
                finding.getUpdatedAt(),
                finding.getAcknowledgedAt(),
                finding.getResolvedAt(),
                finding.getResolutionReason(),
                finding.getResolvedByUserId()
        );
    }
}
