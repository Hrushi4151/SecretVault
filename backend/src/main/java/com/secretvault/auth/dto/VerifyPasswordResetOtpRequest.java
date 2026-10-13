package com.secretvault.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request payload to verify password reset OTP")
public record VerifyPasswordResetOtpRequest(
        @Schema(description = "Registered Gmail address", example = "alice@gmail.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "6-digit OTP code", example = "654321")
        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be exactly 6 digits")
        String otp
) {}
