package com.secretvault.access.privileged.dto;

import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePrivilegedAccessRequest(
        @NotNull(message = "Privileged action is required")
        PrivilegedAction action,

        PrivilegedPolicyScope scopeType,

        UUID projectId,

        UUID environmentId,

        UUID secretId,

        String requestedPermissions,

        @NotNull(message = "Duration in minutes is required")
        @Min(value = 5, message = "Duration must be at least 5 minutes")
        @Max(value = 1440, message = "Duration cannot exceed 1440 minutes (24 hours)")
        Integer durationMinutes,

        @NotBlank(message = "Justification reason is required")
        @Size(min = 10, max = 2000, message = "Justification must be between 10 and 2000 characters")
        String justification,

        UUID targetUserId,

        String stepUpProof
) {}
