package io.secretvault.sdk.resilience;

import java.time.Duration;
import java.util.Random;

/**
 * Handles exponential backoff with jitter for idempotent read requests and rate-limited calls.
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double backoffMultiplier;
    private final Random random = new Random();

    public RetryPolicy() {
        this(3, Duration.ofMillis(100), Duration.ofSeconds(2), 2.0);
    }

    public RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, double backoffMultiplier) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoff = initialBackoff != null ? initialBackoff : Duration.ofMillis(100);
        this.maxBackoff = maxBackoff != null ? maxBackoff : Duration.ofSeconds(2);
        this.backoffMultiplier = backoffMultiplier > 1.0 ? backoffMultiplier : 2.0;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isRetryableStatusCode(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    public Duration computeBackoff(int attempt, Duration serverRetryAfter) {
        if (serverRetryAfter != null && !serverRetryAfter.isNegative()) {
            return serverRetryAfter;
        }

        long backoffMillis = (long) (initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt - 1));
        backoffMillis = Math.min(backoffMillis, maxBackoff.toMillis());

        // Apply full jitter (0 to backoffMillis)
        long jittered = (long) (random.nextDouble() * backoffMillis);
        return Duration.ofMillis(Math.max(10, jittered));
    }
}
