package com.secretvault.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload to initiate password reset OTP")
public record ForgotPasswordRequest(
        @Schema(description = "Registered Gmail address", example = "alice@gmail.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email
) {}
