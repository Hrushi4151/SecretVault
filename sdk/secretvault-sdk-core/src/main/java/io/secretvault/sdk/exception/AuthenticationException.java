package io.secretvault.sdk.exception;

public class AuthenticationException extends SecretVaultException {
    public AuthenticationException(String message) {
        super(message, ErrorCode.SV_AUTH_INVALID, 401, null);
    }

    public AuthenticationException(String message, ErrorCode code) {
        super(message, code, 401, null);
    }

    public AuthenticationException(String message, ErrorCode code, int statusCode, String requestId) {
        super(message, code, statusCode, requestId);
    }
}
