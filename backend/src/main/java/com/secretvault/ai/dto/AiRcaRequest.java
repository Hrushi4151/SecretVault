package com.secretvault.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiRcaRequest(
        @NotBlank(message = "Target type must not be blank (e.g. DEPLOYMENT, SYNC_JOB, ROTATION_JOB)")
        String targetType,

        @NotBlank(message = "Target ID must not be blank")
        String targetId,

        String contextHint
) {
}
