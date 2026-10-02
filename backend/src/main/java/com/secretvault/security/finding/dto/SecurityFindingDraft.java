package com.secretvault.security.finding.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.util.FindingFingerprintGenerator;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable draft descriptor produced by detection rules before idempotency upsert.
 */
public record SecurityFindingDraft(
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        FindingCategory category,
        FindingSeverity severity,
        FindingConfidence confidence,
        String title,
        String safeDescription,
        String remediationGuidance,
        String subjectKey,
        Map<String, Object> evidence
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String generateFingerprint() {
        return FindingFingerprintGenerator.generate(workspaceId, projectId, environmentId, category, subjectKey);
    }

    public String serializeEvidence() {
        if (evidence == null || evidence.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
