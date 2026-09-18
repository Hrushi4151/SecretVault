package com.secretvault.secret.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExecutePromotionRequest(
        @NotNull(message = "Destination environment ID is required")
        UUID destinationEnvironmentId,

        List<String> secretNames,

        Map<String, Integer> expectedDestinationVersions,

        @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
        String reason
) {
}
