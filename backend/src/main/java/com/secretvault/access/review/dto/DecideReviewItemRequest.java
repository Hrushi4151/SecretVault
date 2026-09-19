package com.secretvault.access.review.dto;

import com.secretvault.access.review.entity.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecideReviewItemRequest(
        @NotNull(message = "Decision (KEEP or REVOKE) is required")
        ReviewDecision decision,

        @Size(max = 2000, message = "Reason cannot exceed 2000 characters")
        String decisionReason
) {}
