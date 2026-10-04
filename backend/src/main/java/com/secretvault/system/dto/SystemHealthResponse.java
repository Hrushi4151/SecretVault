package com.secretvault.system.dto;

import java.time.Instant;
import java.util.Map;

public record SystemHealthResponse(
        int healthScore,
        String status, // HEALTHY, DEGRADED, CRITICAL
        Map<String, ComponentHealth> components,
        Instant evaluatedAt
) {
    public record ComponentHealth(
            String status, // UP, DEGRADED, DOWN
            int score,
            String details,
            long latencyMs
    ) {}
}
