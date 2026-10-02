package com.secretvault.provider.model;

/**
 * Standardized, normalized error codes for external provider operations.
 */
public enum ProviderErrorCode {
    PROVIDER_AUTHENTICATION_FAILED,
    PROVIDER_AUTHORIZATION_FAILED,
    PROVIDER_RESOURCE_NOT_FOUND,
    PROVIDER_RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    PROVIDER_INVALID_REQUEST,
    PROVIDER_TIMEOUT,
    PROVIDER_UNSUPPORTED_CAPABILITY,
    PROVIDER_UNKNOWN_ERROR
}
