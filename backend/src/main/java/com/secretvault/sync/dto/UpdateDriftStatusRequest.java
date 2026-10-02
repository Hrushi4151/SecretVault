package com.secretvault.sync.dto;

import com.secretvault.sync.model.DriftStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for updating the triage status of a drift record.
 */
public record UpdateDriftStatusRequest(
        @NotNull(message = "Drift status is required")
        DriftStatus status,
        String resolutionReason
) {}
