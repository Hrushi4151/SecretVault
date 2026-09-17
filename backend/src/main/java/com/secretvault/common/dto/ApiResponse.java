package com.secretvault.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Standard envelope response for all successful REST API payloads.
 *
 * @param <T> Payload data type
 */
@Schema(description = "Standard API response wrapper")
public record ApiResponse<T>(
        @Schema(description = "Operation success flag", example = "true")
        boolean success,

        @Schema(description = "Response payload data")
        T data,

        @Schema(description = "Optional descriptive message", example = "Operation completed successfully")
        String message,

        @Schema(description = "UTC ISO-8601 timestamp")
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "Operation successful", Instant.now());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }
}
