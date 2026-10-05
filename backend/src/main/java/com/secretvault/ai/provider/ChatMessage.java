package com.secretvault.ai.provider;

public record ChatMessage(
        String role, // "system", "user", "assistant", "tool"
        String content,
        String name // optional identifier for tool results
) {
    public ChatMessage(String role, String content) {
        this(role, content, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null);
    }

    public static ChatMessage tool(String id, String toolName, String outputJson) {
        return new ChatMessage("tool", outputJson, toolName);
    }
}
