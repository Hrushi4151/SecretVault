package com.secretvault.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PlanFeedbackRequest(
        @Min(1) @Max(5)
        int rating,
        String comment
) {
}
