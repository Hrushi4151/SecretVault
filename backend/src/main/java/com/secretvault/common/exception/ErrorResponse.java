package com.secretvault.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard RFC-7807 inspired JSON error payload returned on API errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String requestId,
        List<FieldErrorDetail> errors
) {
    public ErrorResponse(int status, String code, String message, String requestId) {
        this(Instant.now(), status, code, message, requestId, null);
    }

    public ErrorResponse(int status, String code, String message, String requestId, List<FieldErrorDetail> errors) {
        this(Instant.now(), status, code, message, requestId, errors);
    }

    public record FieldErrorDetail(String field, String message) {}
}
