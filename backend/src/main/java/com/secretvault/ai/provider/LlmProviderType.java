package com.secretvault.ai.provider;

public enum LlmProviderType {
    DETERMINISTIC_OFFLINE,
    OLLAMA,
    OPENAI,
    CLAUDE_3_5_SONNET,
    OPENAI_GPT4O;

    public static LlmProviderType fromString(String val) {
        if (val == null || val.isBlank()) {
            return DETERMINISTIC_OFFLINE;
        }
        String clean = val.trim().toUpperCase().replace("-", "_");
        for (LlmProviderType type : values()) {
            if (type.name().equalsIgnoreCase(clean)) {
                return type;
            }
        }
        if (clean.contains("OLLAMA")) return OLLAMA;
        if (clean.contains("OPENAI") || clean.contains("GPT")) return OPENAI;
        if (clean.contains("CLAUDE") || clean.contains("ANTHROPIC")) return CLAUDE_3_5_SONNET;
        return DETERMINISTIC_OFFLINE;
    }
}
