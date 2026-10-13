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

    @Test
    @DisplayName("Empty or malformed OR condition evaluates to false (boolean identity)")
    void testEmptyOrReturnsFalse() {
        AutomationCondition emptyOr = new AutomationCondition(null, ConditionOperator.OR, null, List.of());
        assertThat(evaluator.evaluateSingle(emptyOr, testEvent)).isFalse();

        AutomationCondition nullOr = new AutomationCondition(null, ConditionOperator.OR, null, null);
        assertThat(evaluator.evaluateSingle(nullOr, testEvent)).isFalse();
    }

    @Test
    @DisplayName("Empty or malformed NOT condition evaluates to false")
    void testEmptyNotReturnsFalse() {
        AutomationCondition emptyNot = new AutomationCondition(null, ConditionOperator.NOT, null, List.of());
        assertThat(evaluator.evaluateSingle(emptyNot, testEvent)).isFalse();

        AutomationCondition nullNot = new AutomationCondition(null, ConditionOperator.NOT, null, null);
        assertThat(evaluator.evaluateSingle(nullNot, testEvent)).isFalse();
    }

    @Test
    @DisplayName("Null handling is strictly symmetric and safe")
    void testNullHandlingSymmetry() {
        // Event has no 'environmentId' so actual value is null
        AutomationCondition nullMatchesNull = new AutomationCondition("environmentId", ConditionOperator.EQUALS, null, null);
        assertThat(evaluator.evaluateSingle(nullMatchesNull, testEvent)).isTrue();

        AutomationCondition nullNotEqualsNonNull = new AutomationCondition("environmentId", ConditionOperator.NOT_EQUALS, "staging", null);
        assertThat(evaluator.evaluateSingle(nullNotEqualsNonNull, testEvent)).isTrue();

        AutomationCondition nullEqualsNonNull = new AutomationCondition("environmentId", ConditionOperator.EQUALS, "production", null);
        assertThat(evaluator.evaluateSingle(nullEqualsNonNull, testEvent)).isFalse();

        // Non-null actual ('source' is 'security-monitor') compared with null expected
        AutomationCondition nonNullEqualsNull = new AutomationCondition("source", ConditionOperator.EQUALS, null, null);
        assertThat(evaluator.evaluateSingle(nonNullEqualsNull, testEvent)).isFalse();

        AutomationCondition nonNullNotEqualsNull = new AutomationCondition("source", ConditionOperator.NOT_EQUALS, null, null);
        assertThat(evaluator.evaluateSingle(nonNullNotEqualsNull, testEvent)).isTrue();
    }

    @Test
    @DisplayName("Non-numeric metadata gracefully returns false without exception")
    void testMalformedNumericGracefulFallback() {
        AutomationCondition cond = new AutomationCondition("metadata.environment", ConditionOperator.GREATER_THAN, 100, null);
        assertThat(evaluator.evaluateSingle(cond, testEvent)).isFalse();
    }

    @Test
    @DisplayName("Identical inputs deterministically evaluate to identical decisions across multiple runs")
    void testDeterministicEvaluationAcrossIdenticalRuns() {
        AutomationCondition cond = AutomationCondition.and(List.of(
                new AutomationCondition("eventType", ConditionOperator.EQUALS, "SECRET_COMPROMISED", null),
                new AutomationCondition("metadata.rotationAgeDays", ConditionOperator.GREATER_THAN_OR_EQUAL, 90, null)
        ));

        boolean first = evaluator.evaluateSingle(cond, testEvent);
        for (int i = 0; i < 50; i++) {
            assertThat(evaluator.evaluateSingle(cond, testEvent)).isEqualTo(first);
        }
    }
}
