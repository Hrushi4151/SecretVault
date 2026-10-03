package com.secretvault.automation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Sandboxed, structured AST node for policy condition evaluation.
 * Supports simple field comparisons as well as nested AND/OR/NOT blocks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutomationCondition(
        String field,                   // e.g. "eventType", "severity", "environmentType", "rotationAgeDays", "secretKey"
        ConditionOperator operator,     // EQUALS, NOT_EQUALS, IN, CONTAINS, GREATER_THAN, AND, OR, NOT
        Object value,                   // Target literal value or array
        List<AutomationCondition> nested // Child conditions for AND, OR, NOT
) {
    public static AutomationCondition simple(String field, ConditionOperator op, Object val) {
        return new AutomationCondition(field, op, val, null);
    }

    public static AutomationCondition and(List<AutomationCondition> conditions) {
        return new AutomationCondition(null, ConditionOperator.AND, null, conditions);
    }

    public static AutomationCondition or(List<AutomationCondition> conditions) {
        return new AutomationCondition(null, ConditionOperator.OR, null, conditions);
    }

    public static AutomationCondition not(AutomationCondition condition) {
        return new AutomationCondition(null, ConditionOperator.NOT, null, List.of(condition));
    }
}
