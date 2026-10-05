package com.secretvault.ai.provider;

import java.util.List;

public record LlmResponse(
        String text,
        double confidenceScore,
        int tokensUsed,
        long latencyMs,
        String providerName,
        String modelName,
        List<AiToolCall> toolCalls,
        boolean isFinalAnswer
) {
    public LlmResponse(
            String text,
            double confidenceScore,
            int tokensUsed,
            long latencyMs,
            String providerName,
            String modelName
    ) {
        this(text, confidenceScore, tokensUsed, latencyMs, providerName, modelName, List.of(), true);
    }

    public LlmResponse(
            String text,
            double confidenceScore,
            int tokensUsed,
            long latencyMs,
            String providerName,
            String modelName,
            List<AiToolCall> toolCalls
    ) {
        this(text, confidenceScore, tokensUsed, latencyMs, providerName, modelName, toolCalls, toolCalls == null || toolCalls.isEmpty());
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
