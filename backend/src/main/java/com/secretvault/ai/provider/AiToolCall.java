package com.secretvault.ai.provider;

import java.util.Map;

public record AiToolCall(
        String id,
        String toolName,
        Map<String, Object> arguments,
        String rawArgumentsJson
) {
    public AiToolCall(String id, String toolName, Map<String, Object> arguments) {
        this(id, toolName, arguments, null);
    }

    public String name() {
        return toolName;
    }
}
