package io.secretvault.sdk.exception;

public class AuthorizationException extends SecretVaultException {
    public AuthorizationException(String message) {
        super(message, ErrorCode.SV_ACCESS_DENIED, 403, null);
    }

    public AuthorizationException(String message, String requestId) {
        super(message, ErrorCode.SV_ACCESS_DENIED, 403, requestId);
    }
}
