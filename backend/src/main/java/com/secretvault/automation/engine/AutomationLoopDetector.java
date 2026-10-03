package com.secretvault.automation.engine;

import com.secretvault.automation.entity.AutomationPolicy;
import com.secretvault.automation.repository.AutomationExecutionRepository;
import com.secretvault.events.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AutomationLoopDetector {

    private static final Logger log = LoggerFactory.getLogger(AutomationLoopDetector.class);
    private static final int MAX_EXECUTION_DEPTH = 5;
    private static final int MAX_EXECUTIONS_PER_MINUTE = 20;

    private final AutomationExecutionRepository executionRepository;

    public AutomationLoopDetector(AutomationExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    public boolean isLoopOrExceededBudget(AutomationPolicy policy, DomainEvent event, int currentDepth) {
        // 1. Check recursion depth
        if (currentDepth >= MAX_EXECUTION_DEPTH) {
            log.error("CRITICAL TRIPWIRE: Automation loop detected! Policy [{}] exceeded max depth ({}) on event [{}]",
                    policy.getId(), MAX_EXECUTION_DEPTH, event.getEventId());
            return true;
        }

        // 2. Check per-policy rate limit / frequency
        Instant oneMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));
        long recentCount = executionRepository.countByWorkspaceIdAndPolicyIdAndCreatedAtGreaterThanEqual(
                policy.getWorkspaceId(), policy.getId(), oneMinuteAgo
        );

        if (recentCount >= MAX_EXECUTIONS_PER_MINUTE) {
            log.warn("Policy [{}] execution rate limit exceeded ({} in last minute), throttling execution",
                    policy.getId(), recentCount);
            return true;
        }

        return false;
    }

    public boolean isLoopDetected(DomainEvent event) {
        if (event == null) return false;
        // Tripwire: Prevent events caused by automation system from cascading beyond depth
        if (event.getCausationId() != null && event.getCausationId().equals(event.getCorrelationId())
                && "SYSTEM_AUTOMATION".equalsIgnoreCase(event.getSource())) {
            return true;
        }
        return false;
    }
}
