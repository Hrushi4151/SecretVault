package com.secretvault.ai.tool;

import java.util.Map;

public record AiToolDefinition(
        String name,
        String description,
        Map<String, Object> parameterSchema,
        boolean readOnly,
        String requiredPermission
) {
    public AiToolDefinition(String name, String description, Map<String, Object> parameterSchema) {
        this(name, description, parameterSchema, true, null);
    }
}
