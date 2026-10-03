package com.secretvault.automation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Concrete action specification for automation policies.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutomationAction(
        AutomationActionType type,
        Map<String, Object> parameters,
        boolean requireApproval,
        String explanation
) {
    public AutomationAction(AutomationActionType type, Map<String, Object> parameters) {
        this(type, parameters, false, null);
    }

    public static AutomationAction of(AutomationActionType type, Map<String, Object> params) {
        return new AutomationAction(type, params, false, null);
    }

    public static AutomationAction withApproval(AutomationActionType type, Map<String, Object> params, String explanation) {
        return new AutomationAction(type, params, true, explanation);
    }

    public AutomationActionType getType() {
        return type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public boolean isRequireApproval() {
        return requireApproval;
    }

    public String getExplanation() {
        return explanation;
    }
}
