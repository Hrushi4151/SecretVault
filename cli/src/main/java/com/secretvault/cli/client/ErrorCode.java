package com.secretvault.cli.client;

/**
 * Structured client error classification codes.
 */
public enum ErrorCode {
    AUTHENTICATION_FAILED(401),
    TOKEN_EXPIRED(401),
    TOKEN_REFRESH_FAILED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    VALIDATION_ERROR(400),
    RESOURCE_CONFLICT(409),
    RATE_LIMITED(429),
    SERVER_ERROR(500),
    NETWORK_ERROR(0),
    TLS_ERROR(0),
    API_VERSION_MISMATCH(0),
    TIMEOUT(0),
    INVALID_CONTEXT(400),
    COMMAND_EXECUTION_ERROR(1);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static ErrorCode fromHttpStatus(int status) {
        return switch (status) {
            case 400 -> VALIDATION_ERROR;
            case 401 -> AUTHENTICATION_FAILED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 409 -> RESOURCE_CONFLICT;
            case 429 -> RATE_LIMITED;
            case 500, 502, 503, 504 -> SERVER_ERROR;
            default -> SERVER_ERROR;
        };
    }
}
