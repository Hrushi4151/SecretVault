package com.secretvault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource is not found within the caller's organization/scope.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceName, identifier), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}
