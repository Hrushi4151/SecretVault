package com.secretvault.automation.engine;

import com.secretvault.automation.model.AutomationCondition;
import com.secretvault.automation.model.ConditionOperator;
import com.secretvault.events.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ConditionAstEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionAstEvaluator.class);

    public boolean evaluate(List<AutomationCondition> conditions, DomainEvent event) {
        if (conditions == null || conditions.isEmpty()) {
            return true; // No conditions means always matches
        }
        for (AutomationCondition condition : conditions) {
            if (!evaluateSingle(condition, event)) {
                return false;
            }
        }
        return true;
    }

    public boolean evaluate(AutomationCondition condition, DomainEvent event) {
        return evaluateSingle(condition, event);
    }

    public boolean evaluateSingle(AutomationCondition condition, DomainEvent event) {
        if (condition == null) return true;

        ConditionOperator op = condition.operator();
        if (op == null) op = ConditionOperator.EQUALS;

        switch (op) {
            case AND:
                if (condition.nested() == null || condition.nested().isEmpty()) return true;
                return condition.nested().stream().allMatch(c -> evaluateSingle(c, event));

            case OR:
                if (condition.nested() == null || condition.nested().isEmpty()) return true;
                return condition.nested().stream().anyMatch(c -> evaluateSingle(c, event));

            case NOT:
                if (condition.nested() == null || condition.nested().isEmpty()) return true;
                return !evaluateSingle(condition.nested().get(0), event);

            default:
                Object actualValue = extractFieldValue(condition.field(), event);
                return compare(actualValue, op, condition.value());
        }
    }

    private Object extractFieldValue(String field, DomainEvent event) {
        if (field == null || event == null) return null;

        String f = field.trim();
        if (f.equalsIgnoreCase("eventType") || f.equalsIgnoreCase("type")) {
            return event.getEventType() != null ? event.getEventType().name() : null;
        }
        if (f.equalsIgnoreCase("severity")) {
            return event.getSeverity() != null ? event.getSeverity().name() : null;
        }
        if (f.equalsIgnoreCase("workspaceId")) {
            return event.getWorkspaceId() != null ? event.getWorkspaceId().toString() : null;
        }
        if (f.equalsIgnoreCase("projectId")) {
            return event.getProjectId() != null ? event.getProjectId().toString() : null;
        }
        if (f.equalsIgnoreCase("environmentId")) {
            return event.getEnvironmentId() != null ? event.getEnvironmentId().toString() : null;
        }
        if (f.equalsIgnoreCase("secretId")) {
            return event.getSecretId() != null ? event.getSecretId().toString() : null;
        }
        if (f.equalsIgnoreCase("source")) {
            return event.getSource();
        }
        if (f.equalsIgnoreCase("actorType")) {
            return event.getActorType();
        }
        if (f.equalsIgnoreCase("actorId")) {
            return event.getActorId();
        }
        if (f.equalsIgnoreCase("aggregateType")) {
            return event.getAggregateType();
        }
        if (f.equalsIgnoreCase("aggregateId")) {
            return event.getAggregateId();
        }

        // Check metadata
        if (f.startsWith("metadata.") && event.getMetadata() != null) {
            String key = f.substring("metadata.".length());
            return event.getMetadata().get(key);
        }

        if (event.getMetadata() != null && event.getMetadata().containsKey(f)) {
            return event.getMetadata().get(f);
        }

        return null;
    }

    private boolean compare(Object actual, ConditionOperator op, Object expected) {
        if (actual == null && expected == null) {
            return op == ConditionOperator.EQUALS;
        }
        if (actual == null) {
            return op == ConditionOperator.NOT_EQUALS || op == ConditionOperator.NOT_IN;
        }

        String actualStr = String.valueOf(actual).trim();

        switch (op) {
            case EQUALS:
                return actualStr.equalsIgnoreCase(String.valueOf(expected).trim());

            case NOT_EQUALS:
                return !actualStr.equalsIgnoreCase(String.valueOf(expected).trim());

            case CONTAINS:
                return actualStr.toLowerCase().contains(String.valueOf(expected).toLowerCase().trim());

            case STARTS_WITH:
                return actualStr.toLowerCase().startsWith(String.valueOf(expected).toLowerCase().trim());

            case IN:
                if (expected instanceof Collection<?> coll) {
                    return coll.stream().anyMatch(item -> actualStr.equalsIgnoreCase(String.valueOf(item).trim()));
                }
                if (expected instanceof String str) {
                    return Arrays.stream(str.split(","))
                            .map(String::trim)
                            .anyMatch(item -> actualStr.equalsIgnoreCase(item));
                }
                return actualStr.equalsIgnoreCase(String.valueOf(expected).trim());

            case NOT_IN:
                return !compare(actual, ConditionOperator.IN, expected);

            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
                return compareNumeric(actual, op, expected);

            default:
                return false;
        }
    }

    private boolean compareNumeric(Object actual, ConditionOperator op, Object expected) {
        try {
            double a = Double.parseDouble(String.valueOf(actual));
            double b = Double.parseDouble(String.valueOf(expected));

            switch (op) {
                case GREATER_THAN: return a > b;
                case GREATER_THAN_OR_EQUAL: return a >= b;
                case LESS_THAN: return a < b;
                case LESS_THAN_OR_EQUAL: return a <= b;
                default: return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
