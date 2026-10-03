package com.secretvault.access.privileged.dto;

import com.secretvault.access.privileged.model.PrivilegedAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BreakGlassRequest(
        UUID projectId,

        UUID environmentId,

        UUID secretId,

        PrivilegedAction action,

        String requestedPermissions,

        @NotNull(message = "Emergency duration is required")
        @Min(value = 5, message = "Emergency duration must be at least 5 minutes")
        @Max(value = 120, message = "Emergency duration cannot exceed 120 minutes (2 hours)")
        Integer durationMinutes,

        @NotBlank(message = "Emergency justification reason is mandatory")
        @Size(min = 20, max = 2000, message = "Emergency justification must be detailed (at least 20 characters)")
        String justification,

        @NotBlank(message = "Step-up proof is required for break-glass emergency elevation")
        String stepUpProof
) {}
