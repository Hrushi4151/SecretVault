package com.secretvault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an unauthenticated request attempts to access protected resources.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}
