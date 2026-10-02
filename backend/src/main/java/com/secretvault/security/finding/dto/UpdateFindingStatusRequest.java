package com.secretvault.security.finding.dto;

import com.secretvault.security.finding.model.FindingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFindingStatusRequest(
        @NotNull(message = "Status is required")
        FindingStatus status,

        String resolutionReason
) {}
