package com.secretvault.ai.dto;

import com.secretvault.ai.domain.model.TelemetryEvidence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiRcaReportDto(
        UUID id,
        String targetType,
        String targetId,
        String rootCauseSummary,
        String detailedExplanation,
        double confidenceScore,
        List<TelemetryEvidence> telemetryEvidence,
        String remediationStrategy,
        boolean driftHashMismatch,
        String status,
        Instant createdAt
) {
}
