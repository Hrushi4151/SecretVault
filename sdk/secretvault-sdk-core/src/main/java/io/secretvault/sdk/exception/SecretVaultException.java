package io.secretvault.sdk.exception;

/**
 * Base unchecked exception for all SecretVault SDK operations.
 * Redacts any sensitive tokens or secret payload strings from exception messages.
 */
public class SecretVaultException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int statusCode;
    private final String requestId;

    public SecretVaultException(String message, ErrorCode errorCode) {
        this(message, errorCode, 0, null, null);
    }

    public SecretVaultException(String message, ErrorCode errorCode, Throwable cause) {
        this(message, errorCode, 0, null, cause);
    }

    public SecretVaultException(String message, ErrorCode errorCode, int statusCode, String requestId) {
        this(message, errorCode, statusCode, requestId, null);
    }

    public SecretVaultException(String message, ErrorCode errorCode, int statusCode, String requestId, Throwable cause) {
        super(sanitizeMessage(message), cause);
        this.errorCode = errorCode != null ? errorCode : ErrorCode.SV_INTERNAL_ERROR;
        this.statusCode = statusCode;
        this.requestId = requestId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRequestId() {
        return requestId;
    }

    private static String sanitizeMessage(String msg) {
        if (msg == null) return "SecretVault error";
        // Ensure no Bearer tokens or sensitive headers leak into message
        return msg.replaceAll("(?i)bearer\\s+[a-z0-9_\\-\\.]+", "Bearer [REDACTED]")
                  .replaceAll("(?i)password=['\"][^'\"]*['\"]", "password='[REDACTED]'");
    }
}
