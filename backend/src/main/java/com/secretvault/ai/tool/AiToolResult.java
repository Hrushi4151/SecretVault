package com.secretvault.ai.tool;

import java.util.Map;

public record AiToolResult(
        String toolName,
        boolean success,
        String outputJson,
        String error,
        long executionTimeMs,
        Map<String, Object> metadata
) {
    public static AiToolResult ok(String toolName, String outputJson, long executionTimeMs) {
        return new AiToolResult(toolName, true, outputJson, null, executionTimeMs, Map.of());
    }

    public static AiToolResult ok(String toolName, String outputJson, long executionTimeMs, Map<String, Object> metadata) {
        return new AiToolResult(toolName, true, outputJson, null, executionTimeMs, metadata != null ? metadata : Map.of());
    }

    public static AiToolResult error(String toolName, String error, long executionTimeMs) {
        return new AiToolResult(toolName, false, "{\"error\":\"" + error.replace("\"", "\\\"") + "\"}", error, executionTimeMs, Map.of());
    }
}
