package com.secretvault.auth.mfa.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Public metadata returned when an MFA challenge is created during login.
 */
public record MfaChallengeInfo(
        String challengeId,
        UUID userId,
        Instant expiresAt,
        AuthenticationState state
) {}
