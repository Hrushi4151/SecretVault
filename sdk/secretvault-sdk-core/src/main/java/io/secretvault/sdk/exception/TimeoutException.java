package io.secretvault.sdk.exception;

public class TimeoutException extends SecretVaultException {
    public TimeoutException(String message) {
        super(message, ErrorCode.SV_TIMEOUT, 408, null);
    }

    public TimeoutException(String message, Throwable cause) {
        super(message, ErrorCode.SV_TIMEOUT, cause);
    }
}
