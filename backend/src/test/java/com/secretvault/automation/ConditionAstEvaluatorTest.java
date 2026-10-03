package com.secretvault.automation;

import com.secretvault.automation.engine.ConditionAstEvaluator;
import com.secretvault.automation.model.AutomationCondition;
import com.secretvault.automation.model.ConditionOperator;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 13: Condition AST Evaluator Tests")
class ConditionAstEvaluatorTest {

    private ConditionAstEvaluator evaluator;
    private DomainEvent testEvent;
    private UUID workspaceId;
    private UUID secretId;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionAstEvaluator();
        workspaceId = UUID.randomUUID();
        secretId = UUID.randomUUID();

        testEvent = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_COMPROMISED)
                .workspaceId(workspaceId)
                .secretId(secretId)
                .severity(EventSeverity.CRITICAL)
                .source("security-monitor")
                .metadata(Map.of("rotationAgeDays", 120, "environment", "PRODUCTION"))
                .build();
    }

    @Test
    @DisplayName("Empty or null condition list evaluates to true")
    void testEmptyConditions() {
        assertThat(evaluator.evaluate((List<AutomationCondition>) null, testEvent)).isTrue();
        assertThat(evaluator.evaluate(List.of(), testEvent)).isTrue();
    }

    @Test
    @DisplayName("EQUALS condition matches matching event type")
    void testEqualsCondition() {
        AutomationCondition cond = new AutomationCondition("eventType", ConditionOperator.EQUALS, "SECRET_COMPROMISED", null);
        assertThat(evaluator.evaluateSingle(cond, testEvent)).isTrue();

        AutomationCondition nonMatching = new AutomationCondition("eventType", ConditionOperator.EQUALS, "SECRET_ROTATED", null);
        assertThat(evaluator.evaluateSingle(nonMatching, testEvent)).isFalse();
    }

    @Test
    @DisplayName("IN condition matches value in comma-separated list or collection")
    void testInCondition() {
        AutomationCondition cond = new AutomationCondition("severity", ConditionOperator.IN, "HIGH,CRITICAL", null);
        assertThat(evaluator.evaluateSingle(cond, testEvent)).isTrue();

        AutomationCondition collCond = new AutomationCondition("severity", ConditionOperator.IN, List.of("HIGH", "CRITICAL"), null);
        assertThat(evaluator.evaluateSingle(collCond, testEvent)).isTrue();

        AutomationCondition nonMatching = new AutomationCondition("severity", ConditionOperator.IN, "LOW,INFO", null);
        assertThat(evaluator.evaluateSingle(nonMatching, testEvent)).isFalse();
    }

    @Test
    @DisplayName("Numeric GREATER_THAN comparison on metadata field")
    void testNumericGreaterThan() {
        AutomationCondition cond = new AutomationCondition("metadata.rotationAgeDays", ConditionOperator.GREATER_THAN, 90, null);
        assertThat(evaluator.evaluateSingle(cond, testEvent)).isTrue();

        AutomationCondition falseCond = new AutomationCondition("metadata.rotationAgeDays", ConditionOperator.GREATER_THAN, 150, null);
        assertThat(evaluator.evaluateSingle(falseCond, testEvent)).isFalse();
    }

    @Test
    @DisplayName("Nested AND conditions evaluate correctly")
    void testNestedAnd() {
        AutomationCondition c1 = new AutomationCondition("eventType", ConditionOperator.EQUALS, "SECRET_COMPROMISED", null);
        AutomationCondition c2 = new AutomationCondition("severity", ConditionOperator.EQUALS, "CRITICAL", null);
        AutomationCondition andCond = AutomationCondition.and(List.of(c1, c2));

        assertThat(evaluator.evaluateSingle(andCond, testEvent)).isTrue();
    }

    @Test
    @DisplayName("Nested OR conditions evaluate correctly")
    void testNestedOr() {
        AutomationCondition c1 = new AutomationCondition("eventType", ConditionOperator.EQUALS, "SECRET_CREATED", null);
        AutomationCondition c2 = new AutomationCondition("severity", ConditionOperator.EQUALS, "CRITICAL", null);
        AutomationCondition orCond = AutomationCondition.or(List.of(c1, c2));

        assertThat(evaluator.evaluateSingle(orCond, testEvent)).isTrue();
    }

    @Test
    @DisplayName("Nested NOT condition inverts inner match")
    void testNestedNot() {
        AutomationCondition c1 = new AutomationCondition("eventType", ConditionOperator.EQUALS, "SECRET_CREATED", null);
        AutomationCondition notCond = AutomationCondition.not(c1);

        assertThat(evaluator.evaluateSingle(notCond, testEvent)).isTrue();
    }
}
