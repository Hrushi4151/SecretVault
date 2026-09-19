package com.secretvault.access.review.dto;

import com.secretvault.access.model.AccessScope;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotBlank(message = "Campaign name is required")
        @Size(max = 255, message = "Name cannot exceed 255 characters")
        String name,

        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        @NotNull(message = "scopeType is required")
        AccessScope scopeType,

        UUID projectId,

        UUID environmentId,

        @NotNull(message = "dueDate is required")
        @Future(message = "Due date must be in the future")
        Instant dueDate
) {}
