package com.secretvault.ai.provider;

import java.util.Map;

public record AiToolCall(
        String id,
        String toolName,
        Map<String, Object> arguments,
        String rawArgumentsJson,
        String thoughtSignature
) {
    public AiToolCall(String id, String toolName, Map<String, Object> arguments) {
        this(id, toolName, arguments, null, null);
    }

    public AiToolCall(String id, String toolName, Map<String, Object> arguments, String rawArgumentsJson) {
        this(id, toolName, arguments, rawArgumentsJson, null);
    }

    public String name() {
        return toolName;
    }
}
