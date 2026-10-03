package com.secretvault.automation;

import com.secretvault.automation.engine.AutomationLoopDetector;
import com.secretvault.automation.entity.AutomationPolicy;
import com.secretvault.automation.repository.AutomationExecutionRepository;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 13: Automation Loop Detector Tests")
class AutomationLoopDetectorTest {

    @Mock
    private AutomationExecutionRepository executionRepository;

    private AutomationLoopDetector loopDetector;
    private AutomationPolicy testPolicy;
    private DomainEvent testEvent;

    @BeforeEach
    void setUp() {
        loopDetector = new AutomationLoopDetector(executionRepository);
        UUID workspaceId = UUID.randomUUID();

        testPolicy = new AutomationPolicy();
        testPolicy.setId(UUID.randomUUID());
        testPolicy.setWorkspaceId(workspaceId);
        testPolicy.setName("Auto-Rotate On Expiry");

        testEvent = BaseDomainEvent.builder()
                .eventType(EventType.LEASE_EXPIRED)
                .workspaceId(workspaceId)
                .severity(EventSeverity.MEDIUM)
                .source("lease-service")
                .build();
    }

    @Test
    @DisplayName("Tripwire fires when recursion depth reaches MAX_EXECUTION_DEPTH (5)")
    void testRecursionDepthLimit() {
        assertThat(loopDetector.isLoopOrExceededBudget(testPolicy, testEvent, 1)).isFalse();
        assertThat(loopDetector.isLoopOrExceededBudget(testPolicy, testEvent, 4)).isFalse();
        assertThat(loopDetector.isLoopOrExceededBudget(testPolicy, testEvent, 5)).isTrue();
        assertThat(loopDetector.isLoopOrExceededBudget(testPolicy, testEvent, 6)).isTrue();
    }

    @Test
    @DisplayName("Rate limit throttles policy if executions per minute exceed budget (20)")
    void testPerPolicyRateLimit() {
        when(executionRepository.countByWorkspaceIdAndPolicyIdAndCreatedAtGreaterThanEqual(
                eq(testPolicy.getWorkspaceId()), eq(testPolicy.getId()), any(Instant.class)
        )).thenReturn(25L);

        assertThat(loopDetector.isLoopOrExceededBudget(testPolicy, testEvent, 1)).isTrue();
    }

    @Test
    @DisplayName("Execution allowed when within budget")
    void testExecutionAllowedWithinBudget() {
        when(executionRepository.countByWorkspaceIdAndPolicyIdAndCreatedAtGreaterThanEqual(
                eq(testPolicy.getWorkspaceId()), eq(testPolicy.getId()), any(Instant.class)
        )).thenReturn(3L);

        assertThat(loopDetector.isLoopOrExceededBudget(testPolicy, testEvent, 1)).isFalse();
    }
}
