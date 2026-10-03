package com.secretvault.auth.stepup.dto;

import jakarta.validation.constraints.NotBlank;

public record RecoveryCodeStepUpRequest(
        @NotBlank(message = "Recovery code is required")
        String recoveryCode
) {
}
