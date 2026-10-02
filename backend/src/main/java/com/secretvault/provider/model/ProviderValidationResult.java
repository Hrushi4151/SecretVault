package com.secretvault.provider.model;

import java.util.Set;

/**
 * Result of validating an external provider connection and credentials.
 */
public record ProviderValidationResult(
        boolean valid,
        String accountOrTeamName,
        String accountId,
        Set<ProviderCapability> capabilities,
        String errorMessage,
        ProviderErrorCode errorCode
) {
    public static ProviderValidationResult success(String accountOrTeamName, String accountId, Set<ProviderCapability> capabilities) {
        return new ProviderValidationResult(true, accountOrTeamName, accountId, capabilities, null, null);
    }

    public static ProviderValidationResult failure(ProviderErrorCode errorCode, String errorMessage) {
        return new ProviderValidationResult(false, null, null, Set.of(), errorMessage, errorCode);
    }
}
