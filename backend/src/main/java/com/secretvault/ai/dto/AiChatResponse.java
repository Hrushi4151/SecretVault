package com.secretvault.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.secretvault.ai.domain.model.TelemetryEvidence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiChatResponse(
        UUID inquiryId,
        UUID conversationId,
        String prompt,
        String intentType,
        String responseText,
        double confidenceScore,
        String confidenceLevel,
        String modelProvider,
        String modelName,
        long latencyMs,
        List<TelemetryEvidence> sanitizedTelemetryEvidence,
        List<String> recommendations,
        List<String> warnings,
        List<String> limitations,
        String advisoryWarning,
        Instant createdAt
) {
    public AiChatResponse(
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
        this(
                inquiryId,
                null,
                prompt,
                intentType,
                responseText,
                confidenceScore,
                confidenceScore >= 0.90 ? "HIGH" : (confidenceScore >= 0.70 ? "MEDIUM" : "LOW"),
                modelProvider,
                modelName,
                latencyMs,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "AI recommendations are strictly advisory and require human authorization before execution.",
                createdAt
        );
    }

    @JsonProperty("id")
    public UUID id() {
        return inquiryId;
    }

    @JsonProperty("responseContent")
    public String responseContent() {
        return responseText;
    }

    @JsonProperty("intent")
    public String intent() {
        return intentType;
    }

    @JsonProperty("modelUsed")
    public String modelUsed() {
        return modelName;
    }
}
