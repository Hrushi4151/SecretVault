package com.secretvault.auth.stepup.dto;

import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpFactor;

import java.time.Instant;
import java.util.List;

public record StepUpChallengeResponse(
        String challengeId,
        StepUpAction action,
        List<StepUpFactor> supportedFactors,
        Instant expiresAt
) {
}
