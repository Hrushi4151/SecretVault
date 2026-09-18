package com.secretvault.secret.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PromotionPreviewRequest(
        @NotNull(message = "Destination environment ID is required")
        UUID destinationEnvironmentId,

        List<String> secretNames
) {
}
