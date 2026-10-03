package com.secretvault.auth.stepup.dto;

import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpFactor;

import java.time.Instant;

public record StepUpProofResponse(
        String proofToken,
        StepUpAction action,
        StepUpFactor factorUsed,
        Instant expiresAt
) {
}
