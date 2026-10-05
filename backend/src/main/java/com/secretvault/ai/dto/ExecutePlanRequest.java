package com.secretvault.ai.dto;

public record ExecutePlanRequest(
        boolean dryRun,
        String confirmationStatement,
        String stepUpProof
) {
    public ExecutePlanRequest(boolean dryRun, String confirmationStatement) {
        this(dryRun, confirmationStatement, null);
    }
}
