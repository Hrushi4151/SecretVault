package com.secretvault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated identity lacks required permissions or tenant access.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}
