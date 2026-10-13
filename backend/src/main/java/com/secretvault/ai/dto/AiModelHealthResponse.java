package com.secretvault.ai.dto;

public record AiModelHealthResponse(
        String status,
        String activeProvider,
        String modelName,
        boolean zeroKnowledgeEnforced,
        int monthlyTokensUsed,
        int monthlyTokenBudget,
        int remainingTokens
) {
}
