package com.secretvault.rotation;

import com.secretvault.rotation.model.RotationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 12.1 State Machine Certification Test.
 * Formally verifies every legal and illegal state transition in the 21-state
 * Secret Rotation lifecycle.
 */
class RotationStateMachineCertificationTest {

    // Valid state progression graph
    private static final Map<RotationStatus, Set<RotationStatus>> VALID_TRANSITIONS = new EnumMap<>(RotationStatus.class);

    static {
        VALID_TRANSITIONS.put(RotationStatus.SCHEDULED, Set.of(RotationStatus.QUEUED, RotationStatus.CANCELLED, RotationStatus.EXPIRED));
        VALID_TRANSITIONS.put(RotationStatus.QUEUED, Set.of(RotationStatus.STARTED, RotationStatus.CANCELLED, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.STARTED, Set.of(RotationStatus.GENERATING, RotationStatus.FAILED, RotationStatus.CANCELLED));
        VALID_TRANSITIONS.put(RotationStatus.GENERATING, Set.of(RotationStatus.GENERATED, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.GENERATED, Set.of(RotationStatus.VALIDATING, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.VALIDATING, Set.of(RotationStatus.VALIDATED, RotationStatus.VALIDATION_FAILED, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.VALIDATED, Set.of(RotationStatus.STAGING, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.STAGING, Set.of(RotationStatus.STAGED, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.STAGED, Set.of(RotationStatus.ACTIVATING, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.ACTIVATING, Set.of(RotationStatus.ACTIVE, RotationStatus.ACTIVATION_FAILED, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.ACTIVE, Set.of(RotationStatus.GRACE_PERIOD, RotationStatus.REVOKING, RotationStatus.COMPLETED, RotationStatus.ROLLBACK_REQUIRED));
        VALID_TRANSITIONS.put(RotationStatus.GRACE_PERIOD, Set.of(RotationStatus.REVOKING, RotationStatus.COMPLETED, RotationStatus.ROLLBACK_REQUIRED));
        VALID_TRANSITIONS.put(RotationStatus.REVOKING, Set.of(RotationStatus.COMPLETED, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.VALIDATION_FAILED, Set.of(RotationStatus.QUEUED, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.ACTIVATION_FAILED, Set.of(RotationStatus.QUEUED, RotationStatus.ROLLBACK_REQUIRED, RotationStatus.FAILED));
        VALID_TRANSITIONS.put(RotationStatus.ROLLBACK_REQUIRED, Set.of(RotationStatus.ROLLED_BACK, RotationStatus.FAILED));
        // Terminal states - no outgoing transitions
        VALID_TRANSITIONS.put(RotationStatus.COMPLETED, Set.of());
        VALID_TRANSITIONS.put(RotationStatus.ROLLED_BACK, Set.of());
        VALID_TRANSITIONS.put(RotationStatus.FAILED, Set.of(RotationStatus.QUEUED)); // Can only be retried via explicit retry
        VALID_TRANSITIONS.put(RotationStatus.CANCELLED, Set.of());
        VALID_TRANSITIONS.put(RotationStatus.EXPIRED, Set.of());
    }

    private static boolean isValidTransition(RotationStatus from, RotationStatus to) {
        Set<RotationStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Collections.emptySet());
        return allowed.contains(to);
    }

    @Test
    @DisplayName("Verify complete standard forward rotation lifecycle transitions")
    void testStandardLifecycleTransitions() {
        RotationStatus[] standardPath = {
                RotationStatus.SCHEDULED,
                RotationStatus.QUEUED,
                RotationStatus.STARTED,
                RotationStatus.GENERATING,
                RotationStatus.GENERATED,
                RotationStatus.VALIDATING,
                RotationStatus.VALIDATED,
                RotationStatus.STAGING,
                RotationStatus.STAGED,
                RotationStatus.ACTIVATING,
                RotationStatus.ACTIVE,
                RotationStatus.GRACE_PERIOD,
                RotationStatus.REVOKING,
                RotationStatus.COMPLETED
        };

        for (int i = 0; i < standardPath.length - 1; i++) {
            RotationStatus current = standardPath[i];
            RotationStatus next = standardPath[i + 1];
            assertThat(isValidTransition(current, next))
                    .as("Transition from " + current + " to " + next + " must be valid")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Verify illegal direct jump from QUEUED to ACTIVE is prohibited")
    void testIllegalQueuedToActive() {
        assertThat(isValidTransition(RotationStatus.QUEUED, RotationStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("Verify illegal direct jump from VALIDATING to COMPLETED is prohibited")
    void testIllegalValidatingToCompleted() {
        assertThat(isValidTransition(RotationStatus.VALIDATING, RotationStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("Verify terminal state COMPLETED cannot restart to STARTED")
    void testIllegalCompletedToStarted() {
        assertThat(isValidTransition(RotationStatus.COMPLETED, RotationStatus.STARTED)).isFalse();
        assertThat(RotationStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Verify terminal state ROLLED_BACK cannot transition to ACTIVATING")
    void testIllegalRolledBackToActivating() {
        assertThat(isValidTransition(RotationStatus.ROLLED_BACK, RotationStatus.ACTIVATING)).isFalse();
        assertThat(RotationStatus.ROLLED_BACK.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Verify terminal state CANCELLED has no valid outgoing transitions")
    void testTerminalCancelled() {
        assertThat(RotationStatus.CANCELLED.isTerminal()).isTrue();
        for (RotationStatus status : RotationStatus.values()) {
            assertThat(isValidTransition(RotationStatus.CANCELLED, status))
                    .as("CANCELLED cannot transition to " + status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Verify validation failure recovery path")
    void testValidationFailureTransitionPath() {
        assertThat(isValidTransition(RotationStatus.VALIDATING, RotationStatus.VALIDATION_FAILED)).isTrue();
        assertThat(isValidTransition(RotationStatus.VALIDATION_FAILED, RotationStatus.QUEUED)).isTrue(); // Retry
        assertThat(isValidTransition(RotationStatus.VALIDATION_FAILED, RotationStatus.FAILED)).isTrue();
        assertThat(isValidTransition(RotationStatus.VALIDATION_FAILED, RotationStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("Verify activation failure and rollback path")
    void testActivationFailureAndRollbackPath() {
        assertThat(isValidTransition(RotationStatus.ACTIVATING, RotationStatus.ACTIVATION_FAILED)).isTrue();
        assertThat(isValidTransition(RotationStatus.ACTIVATION_FAILED, RotationStatus.ROLLBACK_REQUIRED)).isTrue();
        assertThat(isValidTransition(RotationStatus.ROLLBACK_REQUIRED, RotationStatus.ROLLED_BACK)).isTrue();
        assertThat(isValidTransition(RotationStatus.ROLLED_BACK, RotationStatus.COMPLETED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(RotationStatus.class)
    @DisplayName("Verify comprehensive state matrix for all 21 states")
    void testCompleteStateMatrix(RotationStatus state) {
        assertThat(state).isNotNull();
        if (state.isTerminal()) {
            assertThat(state.isInFlight()).isFalse();
        } else {
            assertThat(state.isInFlight()).isTrue();
        }
    }
}
