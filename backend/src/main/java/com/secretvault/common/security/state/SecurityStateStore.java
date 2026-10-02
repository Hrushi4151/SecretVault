package com.secretvault.common.security.state;

import java.time.Duration;
import java.util.Optional;

/**
 * Reusable contract for short-lived security state (MFA challenges, step-up auth, one-time tokens).
 * Guarantees single-use atomic consumption to prevent replay attacks across distributed instances.
 */
public interface SecurityStateStore {

    /**
     * Stores a short-lived security payload under category and identifier with TTL.
     */
    <T> void put(String category, String identifier, T payload, Duration ttl);

    /**
     * Reads the security payload if present and not expired (without consuming it).
     */
    <T> Optional<T> get(String category, String identifier, Class<T> type);

    /**
     * Atomically retrieves and deletes/consumes the payload in a single operation.
     * Prevents replay attacks where two concurrent requests attempt to use the same security token.
     */
    <T> Optional<T> consumeAtomic(String category, String identifier, Class<T> type);

    /**
     * Checks if a non-expired security state exists for the given category and identifier.
     */
    boolean exists(String category, String identifier);

    /**
     * Deletes the security state explicitly.
     */
    void delete(String category, String identifier);

    /**
     * Atomically increments the failed verification attempt counter for a challenge.
     *
     * @return current attempt count
     */
    long incrementAttempts(String category, String identifier, Duration ttl);
}
