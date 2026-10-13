package com.secretvault.ai.provider;

import com.secretvault.ai.tool.AiToolDefinition;
import java.util.List;

public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        String sanitizedContextJson,
        double temperature,
        int maxTokens,
        List<ChatMessage> conversationHistory,
        List<AiToolDefinition> availableTools
) {
    public LlmRequest(
            String systemPrompt,
            String userPrompt,
            String sanitizedContextJson,
            double temperature,
            int maxTokens
    ) {
        this(systemPrompt, userPrompt, sanitizedContextJson, temperature, maxTokens, List.of(), List.of());
    }

    public LlmRequest(
            String systemPrompt,
            String userPrompt,
            String sanitizedContextJson,
            double temperature,
            int maxTokens,
            List<ChatMessage> conversationHistory
    ) {
        this(systemPrompt, userPrompt, sanitizedContextJson, temperature, maxTokens, conversationHistory, List.of());
    }

    public List<AiToolDefinition> tools() {
        return availableTools;
    }

    public List<ChatMessage> messages() {
        return conversationHistory;
    }
}
