package io.secretvault.sdk.exception;

public enum ErrorCode {
    SV_AUTH_REQUIRED("Authentication credentials are required"),
    SV_AUTH_EXPIRED("Authentication session or token has expired"),
    SV_AUTH_INVALID("Invalid credentials or signature"),
    SV_ACCESS_DENIED("Access denied by SecretVault authorization policy"),
    SV_SECRET_NOT_FOUND("Secret not found in the specified context"),
    SV_SECRET_VERSION_NOT_FOUND("Specified secret version not found"),
    SV_RATE_LIMITED("Request rate limit exceeded"),
    SV_UNAVAILABLE("SecretVault server is currently unreachable or unavailable"),
    SV_TIMEOUT("Network request timed out"),
    SV_CIRCUIT_OPEN("Circuit breaker is open due to consecutive failures"),
    SV_CONFIGURATION_INVALID("Invalid SDK or client configuration"),
    SV_STALE_CACHE_EXPIRED("Cached secret exceeded maximum allowed stale duration"),
    SV_INTERNAL_ERROR("Unexpected internal SDK error");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
