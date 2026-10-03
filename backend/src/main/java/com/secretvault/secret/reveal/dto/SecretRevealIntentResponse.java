package com.secretvault.secret.reveal.dto;

import com.secretvault.secret.reveal.model.RevealPolicyLevel;

import java.time.Instant;
import java.util.List;

public record SecretRevealIntentResponse(
        String intentToken,
        Instant expiresAt,
        int maxDisplayDurationSeconds,
        boolean copyAllowed,
        int clipboardTimeoutSeconds,
        RevealPolicyLevel policyLevel,
        boolean requireReason,
        boolean requireStepUp,
        List<String> allowedStepUpFactors
) {
}
