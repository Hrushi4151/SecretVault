package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Mirror of backend ApiResponse<T> and ErrorResponse.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiEnvelope<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp,
        Integer status,
        String error,
        String code,
        String requestId
) {
}
