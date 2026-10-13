package com.secretvault.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.secretvault.ai.domain.model.TelemetryEvidence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        List<AiResourceReference> resources,
        List<String> recommendations,
        List<AiActionProposal> actions,
        boolean clarificationRequired,
        List<String> warnings,
        List<String> limitations,
        String advisoryWarning,
        Instant createdAt
) {
    public AiChatResponse(
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
        this(
                inquiryId,
                conversationId,
                prompt,
                intentType,
                responseText,
                confidenceScore,
                confidenceLevel,
                modelProvider,
                modelName,
                latencyMs,
                sanitizedTelemetryEvidence,
                List.of(),
                recommendations,
                List.of(),
                false,
                warnings,
                limitations,
                advisoryWarning,
                createdAt
        );
    }

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
                false,
                List.of(),
                List.of("Zero-knowledge metadata context applied", "Plaintext payload unreachable"),
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

    @JsonProperty("evidence")
    public List<TelemetryEvidence> evidence() {
        return sanitizedTelemetryEvidence;
    }
}
