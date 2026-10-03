package com.secretvault.automation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationResult(
        UUID policyId,
        String policyName,
        boolean matched,
        List<ConditionEvaluation> conditionEvaluations,
        List<AutomationAction> plannedActions,
        boolean requiresApproval,
        List<String> requiredPermissions,
        String blastRadiusSummary,
        Map<String, Object> simulationDetails
) {
    public record ConditionEvaluation(
            String field,
            ConditionOperator operator,
            Object expectedValue,
            Object actualValue,
            boolean matched
    ) {}
}
