package io.secretvault.sdk.resilience;

import io.secretvault.sdk.exception.ErrorCode;
import io.secretvault.sdk.exception.SecretVaultException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    @Test
    @DisplayName("CircuitBreaker opens after reaching failure threshold and transitions to HALF_OPEN after cooldown")
    void testCircuitBreakerLifecycle() throws Exception {
        // Threshold 3, cooldown 100ms
        CircuitBreaker cb = new CircuitBreaker(3, Duration.ofMillis(100));

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 3rd failure opens circuit
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // checkPermission should fast-fail
        assertThatThrownBy(cb::checkPermission)
                .isInstanceOf(SecretVaultException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SV_CIRCUIT_OPEN);

        // Wait for cooldown
        Thread.sleep(120);

        // State transitions to HALF_OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Probe success resets circuit to CLOSED
        cb.recordSuccess();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
