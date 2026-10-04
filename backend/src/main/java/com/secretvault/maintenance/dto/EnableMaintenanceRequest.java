package com.secretvault.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Duration;

public record EnableMaintenanceRequest(
        @NotBlank(message = "Maintenance reason is required")
        @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
        String reason,

        String scope,

        boolean allowReadOnly,

        Duration estimatedDuration
) {}
