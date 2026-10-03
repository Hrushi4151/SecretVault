package com.secretvault.automation.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.automation.entity.AutomationPolicy;
import com.secretvault.automation.model.AutomationAction;
import com.secretvault.automation.model.AutomationCondition;
import com.secretvault.automation.model.SimulationResult;
import com.secretvault.events.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AutomationPolicySimulator {

    private static final Logger log = LoggerFactory.getLogger(AutomationPolicySimulator.class);

    private final ConditionAstEvaluator astEvaluator;
    private final ObjectMapper objectMapper;

    public AutomationPolicySimulator(ConditionAstEvaluator astEvaluator, ObjectMapper objectMapper) {
        this.astEvaluator = astEvaluator;
        this.objectMapper = objectMapper;
    }

    public SimulationResult simulate(AutomationPolicy policy, DomainEvent mockEvent) {
        List<AutomationCondition> conditions = parseConditions(policy.getConditionsJson());
        List<AutomationAction> actions = parseActions(policy.getActionsJson());

        List<SimulationResult.ConditionEvaluation> evaluations = new ArrayList<>();
        boolean overallMatch = true;

        for (AutomationCondition c : conditions) {
            boolean condMatched = astEvaluator.evaluateSingle(c, mockEvent);
            if (!condMatched) {
                overallMatch = false;
            }
            evaluations.add(new SimulationResult.ConditionEvaluation(
                    c.field() != null ? c.field() : c.operator().name(),
                    c.operator(),
                    c.value(),
                    c.field() != null ? extractFieldValue(c.field(), mockEvent) : "NESTED",
                    condMatched
            ));
        }

        List<String> requiredPermissions = new ArrayList<>();
        boolean requiresApproval = policy.isApprovalRequired();

        for (AutomationAction act : actions) {
            if (act.requireApproval()) {
                requiresApproval = true;
            }
            requiredPermissions.add(getRequiredPermissionForAction(act.type()));
        }

        String blastRadius = String.format("Policy scope: %s. Action count: %d. %s",
                policy.getScopeType(),
                actions.size(),
                requiresApproval ? "Requires approver review before execution." : "Direct execution allowed.");

        return new SimulationResult(
                policy.getId(),
                policy.getName(),
                overallMatch,
                evaluations,
                overallMatch ? actions : Collections.emptyList(),
                requiresApproval,
                requiredPermissions,
                blastRadius,
                Map.of("dryRunConfigured", policy.isDryRun(), "priority", policy.getPriority())
        );
    }

    private Object extractFieldValue(String field, DomainEvent event) {
        if (field == null || event == null) return null;
        if (field.equalsIgnoreCase("eventType")) return event.getEventType().name();
        if (field.equalsIgnoreCase("severity")) return event.getSeverity().name();
        if (field.equalsIgnoreCase("workspaceId")) return event.getWorkspaceId();
        if (field.equalsIgnoreCase("projectId")) return event.getProjectId();
        if (field.equalsIgnoreCase("environmentId")) return event.getEnvironmentId();
        if (field.equalsIgnoreCase("secretId")) return event.getSecretId();
        if (event.getMetadata() != null && event.getMetadata().containsKey(field)) {
            return event.getMetadata().get(field);
        }
        return null;
    }

    private String getRequiredPermissionForAction(com.secretvault.automation.model.AutomationActionType type) {
        switch (type) {
            case TRIGGER_ROTATION: return "ROTATION_TRIGGER";
            case REVOKE_LEASE: return "SECRET_LEASE_MANAGE";
            case SUSPEND_MACHINE:
            case REVOKE_MACHINE: return "MACHINE_IDENTITY_MANAGE";
            case CREATE_SECURITY_INCIDENT:
            case CREATE_SECURITY_FINDING: return "SECURITY_MANAGE";
            case CREATE_WEBHOOK_DELIVERY: return "INTEGRATION_MANAGE";
            default: return "AUTOMATION_EXECUTE";
        }
    }

    private List<AutomationCondition> parseConditions(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<AutomationCondition>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<AutomationAction> parseActions(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<AutomationAction>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
