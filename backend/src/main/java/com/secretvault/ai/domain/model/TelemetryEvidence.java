package com.secretvault.ai.domain.model;

import java.time.Instant;

/**
 * Structured evidence factor explaining an AI diagnostic conclusion or RCA finding.
 */
public record TelemetryEvidence(
        String factorCode,
        String summary,
        String detail,
        String sourceResource,
        Instant timestamp
) {
}
