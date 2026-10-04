package com.secretvault.ai.provider;

public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        String sanitizedContextJson,
        double temperature,
        int maxTokens
) {
}
