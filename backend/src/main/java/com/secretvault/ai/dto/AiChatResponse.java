package com.secretvault.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiChatResponse(
        UUID inquiryId,
        String prompt,
        String intentType,
        String responseText,
        double confidenceScore,
        String modelProvider,
        String modelName,
        long latencyMs,
        Instant createdAt
) {
}
