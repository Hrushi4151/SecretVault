package com.secretvault.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.entity.AiRemediationPlan;
import com.secretvault.ai.domain.model.AiPlanStatus;
import com.secretvault.ai.domain.repository.AiRemediationPlanRepository;
import com.secretvault.ai.dto.AiRemediationPlanDto;
import com.secretvault.ai.dto.ExecutePlanRequest;
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

@DisplayName("AiRemediationExecutionGateway Execution Tests")
class AiRemediationExecutionGatewayTest {

    private AiRemediationPlanRepository planRepository;
    private AiRecommendationEngine recommendationEngine;
    private AuditService auditService;
    private SecurityEventService securityEventService;
    private ObjectMapper objectMapper;
    private AiRemediationExecutionGateway executionGateway;

    @BeforeEach
    void setUp() {
        planRepository = mock(AiRemediationPlanRepository.class);
        recommendationEngine = mock(AiRecommendationEngine.class);
        auditService = mock(AuditService.class);
        securityEventService = mock(SecurityEventService.class);
        objectMapper = new ObjectMapper();

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
    @DisplayName("Dry-run execution previews without transitioning state to EXECUTED")
    void testDryRunExecution() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.PENDING_APPROVAL);
        plan.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));
        when(recommendationEngine.toDto(plan)).thenReturn(new AiRemediationPlanDto(
                planId, null, "TEST", "Title", "Desc", null, 0.95, "SECRET", "sec-1", null, null, null, AiPlanStatus.PENDING_APPROVAL, plan.getExpiresAt(), null, null, Instant.now()
        ));

        AiRemediationPlanDto result = executionGateway.executePlan(workspaceId, planId, new ExecutePlanRequest(true, "Dry run preview"), null);
        assertNotNull(result);
        assertEquals(AiPlanStatus.PENDING_APPROVAL, plan.getStatus());
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("Authoritative execution transitions approved plan to EXECUTED and logs audit trail")
    void testAuthoritativeExecution() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.APPROVED);
        plan.setTitle("Authoritative Plan");
        plan.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));
        when(recommendationEngine.toDto(any())).thenAnswer(invocation -> {
            AiRemediationPlan p = invocation.getArgument(0);
            return new AiRemediationPlanDto(
                    planId, null, "TEST", p.getTitle(), "Desc", null, 0.95, "SECRET", "sec-1", null, null, null, p.getStatus(), p.getExpiresAt(), null, null, Instant.now()
            );
        });

        AiRemediationPlanDto result = executionGateway.executePlan(workspaceId, planId, new ExecutePlanRequest(false, "Confirm execute"), actorId);
        assertNotNull(result);
        assertEquals(AiPlanStatus.EXECUTED, plan.getStatus());
        assertNotNull(plan.getExecutedAt());
        verify(auditService).logSuccess(any(), any(), eq(planId), eq(actorId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Should reject execution if plan has expired")
    void testRejectExpiredPlan() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.APPROVED);
        plan.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS)); // Expired!

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        assertThrows(IllegalStateException.class, () ->
                executionGateway.executePlan(workspaceId, planId, new ExecutePlanRequest(false, "Execute"), null)
        );
        assertEquals(AiPlanStatus.EXPIRED, plan.getStatus());
    }
}
