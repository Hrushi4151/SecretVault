package com.secretvault.ai.provider;

public record LlmResponse(
        String text,
        double confidenceScore,
        int tokensUsed,
        long latencyMs,
        String providerName,
        String modelName
) {
}
