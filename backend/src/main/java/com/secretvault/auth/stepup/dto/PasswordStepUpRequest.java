package com.secretvault.auth.stepup.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordStepUpRequest(
        @NotBlank(message = "Password is required")
        String password
) {
}
