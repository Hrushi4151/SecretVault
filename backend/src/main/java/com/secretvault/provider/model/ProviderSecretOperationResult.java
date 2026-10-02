package com.secretvault.provider.model;

/**
 * Result of pushing, updating, or deleting a secret on an external provider.
 */
public record ProviderSecretOperationResult(
        boolean success,
        String operation,
        String key,
        String providerSecretId,
        ProviderErrorCode errorCode,
        String errorMessage
) {
    public static ProviderSecretOperationResult success(String operation, String key, String providerSecretId) {
        return new ProviderSecretOperationResult(true, operation, key, providerSecretId, null, null);
    }

    public static ProviderSecretOperationResult failure(String operation, String key, ProviderErrorCode errorCode, String errorMessage) {
        return new ProviderSecretOperationResult(false, operation, key, null, errorCode, errorMessage);
    }
}
