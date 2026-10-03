package io.secretvault.sdk.exception;

import java.time.Duration;

public class RateLimitException extends SecretVaultException {

    private final Duration retryAfter;

    public RateLimitException(String message, Duration retryAfter, String requestId) {
        super(message != null ? message : "Rate limit exceeded", ErrorCode.SV_RATE_LIMITED, 429, requestId);
        this.retryAfter = retryAfter != null ? retryAfter : Duration.ofSeconds(1);
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
