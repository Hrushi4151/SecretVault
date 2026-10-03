package io.secretvault.sdk.exception;

public class SecretVaultUnavailableException extends SecretVaultException {
    public SecretVaultUnavailableException(String message) {
        super(message, ErrorCode.SV_UNAVAILABLE, 503, null);
    }

    public SecretVaultUnavailableException(String message, Throwable cause) {
        super(message, ErrorCode.SV_UNAVAILABLE, cause);
    }

    public SecretVaultUnavailableException(String message, int statusCode, String requestId) {
        super(message, ErrorCode.SV_UNAVAILABLE, statusCode, requestId);
    }
}
