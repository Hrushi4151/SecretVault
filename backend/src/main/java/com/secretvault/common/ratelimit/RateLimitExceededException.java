package com.secretvault.common.ratelimit;

import com.secretvault.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a client exceeds allowed request rate limits.
 * Triggers HTTP 429 Too Many Requests response with standard sanitized error body
 * and Retry-After header.
 */
public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        this("Too many requests. Please try again later.", retryAfterSeconds);
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
