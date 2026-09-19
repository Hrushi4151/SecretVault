package com.secretvault.access.jit.dto;

import com.secretvault.access.model.AccessPermission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SubmitJitRequest(
        UUID projectId,

        @NotNull(message = "environmentId is required")
        UUID environmentId,

        UUID secretId,

        @NotNull(message = "requestedPermission is required")
        AccessPermission requestedPermission,

        @Min(value = 5, message = "Duration must be at least 5 minutes")
        @Max(value = 240, message = "Duration cannot exceed 240 minutes (4 hours)")
        int durationMinutes,

        @NotBlank(message = "Operational justification reason is required")
        @Size(min = 10, max = 2000, message = "Reason must be between 10 and 2000 characters")
        String reason
) {}
