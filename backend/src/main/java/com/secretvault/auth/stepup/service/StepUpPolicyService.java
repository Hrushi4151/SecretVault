package com.secretvault.auth.stepup.service;

import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;

import java.util.UUID;

/**
 * Centralized policy service determining whether sensitive operations require Step-Up Authentication.
 */
public interface StepUpPolicyService {

    /**
     * Evaluates whether a given user action on the specified resource context requires step-up re-authentication.
     *
     * @param userId the authenticated user requesting the action
     * @param action the sensitive operation being attempted
     * @param context the target resource context
     * @return true if step-up authentication is mandatory before executing the action
     */
    boolean requiresStepUp(UUID userId, StepUpAction action, StepUpContext context);
}
