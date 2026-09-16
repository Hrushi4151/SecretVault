package com.secretvault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when domain validation fails.
 */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }
}
