package com.secretvault.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record AiChatRequest(
        @NotBlank(message = "Prompt must not be blank")
        String prompt,
        String intentType,
        UUID conversationId,
        UUID targetProjectId,
        UUID targetEnvironmentId,
        String targetType,
        String targetId,
        String contextHint
) {
    public AiChatRequest(String prompt, String intentType, UUID targetProjectId, UUID targetEnvironmentId) {
        this(prompt, intentType, null, targetProjectId, targetEnvironmentId, null, null, null);
    }

    public AiChatRequest(String prompt, String intentType, String targetType, String targetId, UUID conversationId, String contextHint) {
        this(prompt, intentType, conversationId, null, null, targetType, targetId, contextHint);
    }
}
