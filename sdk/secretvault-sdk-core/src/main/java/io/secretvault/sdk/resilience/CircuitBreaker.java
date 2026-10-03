package io.secretvault.sdk.resilience;

import io.secretvault.sdk.exception.ErrorCode;
import io.secretvault.sdk.exception.SecretVaultException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe Circuit Breaker protecting backend from thread stampedes during service degradation or outages.
 */
public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration cooldownDuration;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> lastStateChange = new AtomicReference<>(Instant.now());

    public CircuitBreaker() {
        this(5, Duration.ofSeconds(10));
    }

    public CircuitBreaker(int failureThreshold, Duration cooldownDuration) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownDuration = cooldownDuration != null ? cooldownDuration : Duration.ofSeconds(10);
    }

    public State getState() {
        checkCooldown();
        return state.get();
    }

    public void checkPermission() {
        State current = getState();
        if (current == State.OPEN) {
            throw new SecretVaultException(
                    "Circuit breaker is OPEN. Fast-failing secret retrieval until cooldown expires at " +
                            lastStateChange.get().plus(cooldownDuration),
                    ErrorCode.SV_CIRCUIT_OPEN
            );
        }
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        if (state.get() != State.CLOSED) {
            transitionTo(State.CLOSED);
        }
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold && state.get() == State.CLOSED) {
            transitionTo(State.OPEN);
        } else if (state.get() == State.HALF_OPEN) {
            transitionTo(State.OPEN);
        }
    }

    public void reset() {
        consecutiveFailures.set(0);
        transitionTo(State.CLOSED);
    }

    private void checkCooldown() {
        if (state.get() == State.OPEN) {
            Instant openedAt = lastStateChange.get();
            if (Instant.now().isAfter(openedAt.plus(cooldownDuration))) {
                transitionTo(State.HALF_OPEN);
            }
        }
    }

    private void transitionTo(State newState) {
        state.set(newState);
        lastStateChange.set(Instant.now());
    }
}
