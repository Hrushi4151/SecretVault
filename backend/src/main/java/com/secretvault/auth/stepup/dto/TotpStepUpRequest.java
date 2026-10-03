package com.secretvault.auth.stepup.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpStepUpRequest(
        @NotBlank(message = "Verification code is required")
        String code
) {
}
