package com.secretvault.auth.stepup.dto;

import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
import jakarta.validation.constraints.NotNull;

public record StepUpChallengeRequest(
        @NotNull(message = "Step-up action is required")
        StepUpAction action,
        StepUpContext context
) {
}
