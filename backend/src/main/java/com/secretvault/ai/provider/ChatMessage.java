package com.secretvault.ai.provider;

import java.util.List;

public record ChatMessage(
        String role, // "system", "user", "assistant", "tool"
        String content,
        String name, // optional identifier for tool results
        List<AiToolCall> toolCalls
) {
    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }

    public ChatMessage(String role, String content, String name) {
        this(role, content, name, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage assistantWithToolCalls(String content, List<AiToolCall> toolCalls) {
        return new ChatMessage("assistant", content, null, toolCalls);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage tool(String id, String toolName, String outputJson) {
        return new ChatMessage("tool", outputJson, toolName, null);
    }

    public String toolName() {
        return name;
    }
}
