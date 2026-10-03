package com.secretvault.health.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SecretHealthEvaluation(
        UUID secretId,
        String secretName,
        UUID workspaceId,
        UUID environmentId,
        String environmentName,
        SecretHealthStatus status,
        int healthScore, // 0-100 (100 = optimal)
        Instant evaluatedAt,
        List<RiskFactor> riskFactors,
        boolean rotationConfigured,
        Instant lastRotatedAt,
        Instant nextRotationDueAt,
        long activeLeaseCount,
        long activeConsumerCount
) {}
