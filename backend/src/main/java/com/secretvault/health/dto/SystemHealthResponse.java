package com.secretvault.health.dto;

import java.time.Instant;

/**
 * Baseline health and status response DTO.
 */
public record SystemHealthResponse(
        String status,
        Instant timestamp,
        String service,
        String version
) {
    public static SystemHealthResponse up(String version) {
        return new SystemHealthResponse("UP", Instant.now(), "secretvault-backend", version);
    }
}
