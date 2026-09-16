package com.secretvault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a resource creation/update conflicts with existing state (e.g. duplicate key).
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
    }
}
