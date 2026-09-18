package com.secretvault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all business and domain errors in SecretVault.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(String message, Throwable cause, HttpStatus status, String code) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public static ApiException notFound(String message) {
        return new ApiException(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public static ApiException conflict(String message) {
        return new ApiException(message, HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST, code);
    }

    public static ApiException branchesNotAllowed(String envName, Object envType) {
        return new ApiException(
                "Feature branches are only permitted in DEVELOPMENT environments. Environment [" + envName + "] is " + envType + ".",
                HttpStatus.BAD_REQUEST,
                "BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT"
        );
    }

    public static ApiException internal(String code, String message) {
        return new ApiException(message, HttpStatus.INTERNAL_SERVER_ERROR, code);
    }

    public static ApiException internal(String code, String message, Throwable cause) {
        return new ApiException(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, code);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
