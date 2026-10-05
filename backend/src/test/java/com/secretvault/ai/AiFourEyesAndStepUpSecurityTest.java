package com.secretvault.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.entity.AiRemediationPlan;
import com.secretvault.ai.domain.model.AiPlanStatus;
import com.secretvault.ai.domain.model.AiRiskLevel;
import com.secretvault.ai.domain.repository.AiRemediationPlanRepository;
import com.secretvault.ai.dto.AiPlanGenerateRequest;
import com.secretvault.ai.dto.AiRemediationPlanDto;
import com.secretvault.ai.dto.ExecutePlanRequest;
import com.secretvault.ai.service.AiRecommendationEngine;
import com.secretvault.ai.service.AiRemediationExecutionGateway;
import com.secretvault.audit.service.AuditService;
import com.secretvault.security.event.service.SecurityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Phase 15: AI Four-Eyes Separation of Duties, Step-Up MFA & Plan Integrity Tests")
class AiFourEyesAndStepUpSecurityTest {

    private AiRemediationPlanRepository planRepository;
    private AuditService auditService;
    private SecurityEventService securityEventService;
    private ObjectMapper objectMapper;
    private AiRecommendationEngine recommendationEngine;
    private AiRemediationExecutionGateway executionGateway;

    @BeforeEach
    void setUp() {
        planRepository = mock(AiRemediationPlanRepository.class);
        auditService = mock(AuditService.class);
        securityEventService = mock(SecurityEventService.class);
        objectMapper = new ObjectMapper();

        recommendationEngine = new AiRecommendationEngine(
                planRepository,
                auditService,
                securityEventService,
                objectMapper
        );

        executionGateway = new AiRemediationExecutionGateway(
                planRepository,
                recommendationEngine,
                auditService,
                securityEventService,
                objectMapper
        );

        when(planRepository.save(any(AiRemediationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("FOUR-EYES: Approver A approving twice must be strictly rejected")
    void testFourEyesRejectsIdenticalApprover() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.PENDING_APPROVAL);
        plan.setRequiresFourEyes(true);
        plan.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        // First eye by User A
        AiRemediationPlanDto firstApproval = recommendationEngine.approvePlan(workspaceId, planId, userA);
        assertEquals(AiPlanStatus.PENDING_APPROVAL, firstApproval.status());
        assertEquals(userA, plan.getReviewedByUserId());
        assertNull(plan.getSecondReviewedByUserId());

        // Second eye attempt by the same User A -> Must Fail
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                recommendationEngine.approvePlan(workspaceId, planId, userA)
        );
        assertTrue(ex.getMessage().contains("Four-eyes violation") || ex.getMessage().contains("different authorized user"));
    }

    @Test
    @DisplayName("FOUR-EYES: Approver A followed by distinct Approver B completes approval and seals fingerprint")
    void testFourEyesSucceedsWithDistinctApprovers() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setPlanType("AUTOMATED_ROLLOVER");
        plan.setTargetResourceType("SECRET");
        plan.setTargetResourceId("db-url-key");
        plan.setStatus(AiPlanStatus.PENDING_APPROVAL);
        plan.setRequiresFourEyes(true);
        plan.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        // First eye by User A
        recommendationEngine.approvePlan(workspaceId, planId, userA);
        assertEquals(userA, plan.getReviewedByUserId());
        assertEquals(AiPlanStatus.PENDING_APPROVAL, plan.getStatus());

        // Second eye by User B
        AiRemediationPlanDto finalApproval = recommendationEngine.approvePlan(workspaceId, planId, userB);
        assertEquals(AiPlanStatus.APPROVED, finalApproval.status());
        assertEquals(userB, plan.getSecondReviewedByUserId());
        assertNotNull(plan.getPlanFingerprint());
    }

    @Test
    @DisplayName("PLAN VERSIONING: Modifying an approved plan increments version and invalidates prior approvals")
    void testPlanModificationIncrementsVersionAndInvalidatesApproval() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setVersion(1);
        plan.setStatus(AiPlanStatus.APPROVED);
        plan.setReviewedByUserId(userA);
        plan.setPlanFingerprint("sealed-fingerprint-v1");
        plan.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        // Modify plan
        AiRemediationPlanDto modified = recommendationEngine.modifyPlan(
                workspaceId,
                planId,
                "[{\"stepNumber\":1,\"actionType\":\"MODIFIED_STEP\"}]",
                "{\"diff\":\"updated\"}",
                UUID.randomUUID()
        );

        assertEquals(2, modified.version());
        assertEquals(AiPlanStatus.PENDING_APPROVAL, modified.status());
        assertNull(plan.getReviewedByUserId());
        assertNull(plan.getPlanFingerprint());

        // Execution of modified plan before re-approval must fail
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                executionGateway.executePlan(workspaceId, planId, new ExecutePlanRequest(false, "Run"), userA)
        );
        assertTrue(ex.getMessage().contains("must be APPROVED before authoritative execution"));
    }

    @Test
    @DisplayName("STEP-UP: High-risk execution without valid Step-Up proof is rejected")
    void testStepUpEnforcementOnExecution() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.APPROVED);
        plan.setRequiresStepUp(true);
        plan.setPlanFingerprint(recommendationEngine.computePlanFingerprint(plan));
        plan.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        // Missing stepUpProof
        SecurityException ex = assertThrows(SecurityException.class, () ->
                executionGateway.executePlan(workspaceId, planId, new ExecutePlanRequest(false, "Execute without StepUp", null), userA)
        );
        assertTrue(ex.getMessage().contains("Step-Up Authentication Required"));

        // With valid stepUpProof
        AiRemediationPlanDto executed = executionGateway.executePlan(
                workspaceId,
                planId,
                new ExecutePlanRequest(false, "Execute with StepUp", "stepup_proof_token_valid_123"),
                userA
        );
        assertEquals(AiPlanStatus.EXECUTED, executed.status());
    }

    @Test
    @DisplayName("IDEMPOTENCY: Repeated execution requests return existing result safely without duplicate side-effects")
    void testExecutionIdempotency() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.EXECUTED);
        plan.setExecutedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        plan.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        // Calling execute again on already EXECUTED plan should return existing DTO safely
        AiRemediationPlanDto result = executionGateway.executePlan(workspaceId, planId, new ExecutePlanRequest(false, "Repeat"), userA);
        assertNotNull(result);
        assertEquals(AiPlanStatus.EXECUTED, result.status());
        verify(auditService, never()).logSuccess(any(), any(), any(), any(), any(), any());
    }
}
