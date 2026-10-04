package com.secretvault.ai.dto;

public record ExecutePlanRequest(
        boolean dryRun,
        String confirmationStatement
) {
}
