package com.secretvault.ai.tool;

import java.util.Map;

/**
 * Generic, pluggable AI tool contract for controlled SecretVault operations and data access.
 */
public interface AiTool {

    String getName();

    String getDescription();

    Map<String, Object> getParameterSchema();

    AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments);

    default boolean isReadOnly() {
        return true;
    }

    default String getRequiredPermission() {
        return null;
    }

    default AiToolDefinition getDefinition() {
        return new AiToolDefinition(getName(), getDescription(), getParameterSchema(), isReadOnly(), getRequiredPermission());
    }
}
