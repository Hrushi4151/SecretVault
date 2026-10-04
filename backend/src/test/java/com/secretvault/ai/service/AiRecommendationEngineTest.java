package com.secretvault.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.entity.AiRemediationPlan;
import com.secretvault.ai.domain.model.AiPlanStatus;
import com.secretvault.ai.domain.model.AiRiskLevel;
import com.secretvault.ai.domain.repository.AiRemediationPlanRepository;
import com.secretvault.ai.dto.AiRemediationPlanDto;
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

@DisplayName("AiRecommendationEngine Lifecycle Tests")
class AiRecommendationEngineTest {

    private AiRemediationPlanRepository planRepository;
    private AuditService auditService;
    private SecurityEventService securityEventService;
    private ObjectMapper objectMapper;
    private AiRecommendationEngine recommendationEngine;

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

        when(planRepository.save(any(AiRemediationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should approve a pending remediation plan")
    void testApprovePlan() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.PENDING_APPROVAL);
        plan.setTitle("Remediate Stale Credentials");
        plan.setDescription("Trigger rotation");
        plan.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        AiRemediationPlanDto result = recommendationEngine.approvePlan(workspaceId, planId, reviewerId);
        assertEquals(AiPlanStatus.APPROVED, result.status());
        verify(auditService).logSuccess(any(), any(), eq(planId), eq(reviewerId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Should reject a pending remediation plan with reason")
    void testRejectPlan() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.PENDING_APPROVAL);
        plan.setTitle("Reconcile Vercel Drift");
        plan.setDescription("Push updated key");
        plan.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        AiRemediationPlanDto result = recommendationEngine.rejectPlan(workspaceId, planId, reviewerId, "Maintenance window active");
        assertEquals(AiPlanStatus.REJECTED, result.status());
        assertEquals("Maintenance window active", result.feedbackComment());
    }

    @Test
    @DisplayName("Should record human feedback on plan")
    void testRecordFeedback() {
        UUID workspaceId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        AiRemediationPlan plan = new AiRemediationPlan();
        plan.setId(planId);
        plan.setWorkspaceId(workspaceId);
        plan.setStatus(AiPlanStatus.EXECUTED);
        plan.setTitle("Plan feedback test");
        plan.setDescription("Test");
        plan.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

        when(planRepository.findByIdAndWorkspaceId(planId, workspaceId)).thenReturn(Optional.of(plan));

        AiRemediationPlanDto result = recommendationEngine.recordFeedback(workspaceId, planId, 5, "Highly accurate diagnosis");
        assertEquals(5, result.feedbackRating());
        assertEquals("Highly accurate diagnosis", result.feedbackComment());
    }
}
