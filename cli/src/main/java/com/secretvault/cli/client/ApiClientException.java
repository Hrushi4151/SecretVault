package com.secretvault.cli.client;

import com.secretvault.cli.security.RedactionHelper;

/**
 * Structured API Client exception containing HTTP status, error code, request ID,
 * and sanitized error messages.
 */
public class ApiClientException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;
    private final String requestId;
    private final long retryAfterSeconds;

    public ApiClientException(ErrorCode errorCode, String message) {
        this(errorCode, errorCode.getHttpStatus(), message, null, 0, null);
    }

    public ApiClientException(ErrorCode errorCode, int httpStatus, String message, String requestId) {
        this(errorCode, httpStatus, message, requestId, 0, null);
    }

    public ApiClientException(ErrorCode errorCode, int httpStatus, String message, String requestId, long retryAfterSeconds, Throwable cause) {
        super(RedactionHelper.redact(message), cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.requestId = requestId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getRequestId() {
        return requestId;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public static ApiClientException authFailed(String message) {
        return new ApiClientException(ErrorCode.AUTHENTICATION_FAILED, 401, message, null);
    }

    public static ApiClientException forbidden(String message) {
        return new ApiClientException(ErrorCode.FORBIDDEN, 403, message, null);
    }

    public static ApiClientException notFound(String message) {
        return new ApiClientException(ErrorCode.NOT_FOUND, 404, message, null);
    }

    public static ApiClientException rateLimited(String message, long retryAfterSeconds) {
        return new ApiClientException(ErrorCode.RATE_LIMITED, 429, message, null, retryAfterSeconds, null);
    }

    public static ApiClientException networkError(String message, Throwable cause) {
        return new ApiClientException(ErrorCode.NETWORK_ERROR, 0, "Network communication failure: " + message, null, 0, cause);
    }

    public static ApiClientException tlsError(String message, Throwable cause) {
        return new ApiClientException(ErrorCode.TLS_ERROR, 0, "TLS certificate validation failed: " + message, null, 0, cause);
    }
}
