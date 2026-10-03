package com.secretvault.automation.model;

/**
 * Sandboxed comparison and logical operators for automation policy AST.
 */
public enum ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    IN,
    NOT_IN,
    CONTAINS,
    STARTS_WITH,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    AND,
    OR,
    NOT
}
