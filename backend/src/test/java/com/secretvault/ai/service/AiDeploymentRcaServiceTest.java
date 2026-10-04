package com.secretvault.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.entity.AiRcaReport;
import com.secretvault.ai.domain.entity.AiRemediationPlan;
import com.secretvault.ai.domain.repository.AiRcaReportRepository;
import com.secretvault.ai.domain.repository.AiRemediationPlanRepository;
import com.secretvault.ai.dto.AiRcaReportDto;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.audit.service.AuditService;
import com.secretvault.security.event.service.SecurityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AiDeploymentRcaService Tests")
class AiDeploymentRcaServiceTest {

    private AiRcaReportRepository rcaRepository;
    private AiRemediationPlanRepository planRepository;
    private AiContextSanitizer sanitizer;
    private AuditService auditService;
    private SecurityEventService securityEventService;
    private ObjectMapper objectMapper;
    private AiDeploymentRcaService rcaService;

    @BeforeEach
    void setUp() {
        rcaRepository = mock(AiRcaReportRepository.class);
        planRepository = mock(AiRemediationPlanRepository.class);
        sanitizer = new AiContextSanitizer();
        auditService = mock(AuditService.class);
        securityEventService = mock(SecurityEventService.class);
        objectMapper = new ObjectMapper();

        rcaService = new AiDeploymentRcaService(
                rcaRepository,
                planRepository,
                sanitizer,
                auditService,
                securityEventService,
                objectMapper
        );

        when(rcaRepository.save(any(AiRcaReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.save(any(AiRemediationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should generate deployment failure RCA and automatically create linked remediation plan")
    void testAnalyzeDeploymentFailure() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AiRcaReportDto report = rcaService.analyzeFailure(
                workspaceId,
                "DEPLOYMENT",
                "dep-pay-worker-01",
                "container crashed during initialization with Stripe token sk_live_1234567890abcdef1234",
                userId
        );

        assertNotNull(report);
        assertEquals("DEPLOYMENT", report.targetType());
        assertEquals("dep-pay-worker-01", report.targetId());
        assertTrue(report.rootCauseSummary().contains("Runtime Secret Hydration Version Mismatch"));
        assertFalse(report.detailedExplanation().contains("sk_live_1234567890abcdef1234")); // Sanitized!
        assertTrue(report.confidenceScore() >= 0.95);
        assertFalse(report.telemetryEvidence().isEmpty());

        // Verify linked plan was created
        ArgumentCaptor<AiRemediationPlan> planCaptor = ArgumentCaptor.forClass(AiRemediationPlan.class);
        verify(planRepository).save(planCaptor.capture());
        AiRemediationPlan linkedPlan = planCaptor.getValue();
        assertEquals(workspaceId, linkedPlan.getWorkspaceId());
        assertNotNull(linkedPlan.getRemediationStepsJson());
        assertNotNull(linkedPlan.getBlastRadiusJson());

        // Verify audit log
        verify(auditService).logSuccess(any(), any(), any(), eq(userId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Should generate sync job RCA for provider rate limits")
    void testAnalyzeSyncFailure() {
        UUID workspaceId = UUID.randomUUID();

        AiRcaReportDto report = rcaService.analyzeFailure(
                workspaceId,
                "SYNC_JOB",
                "sync-vercel-job-42",
                "Rate limit exceeded on Vercel API",
                null
        );

        assertNotNull(report);
        assertEquals("SYNC_JOB", report.targetType());
        assertTrue(report.rootCauseSummary().contains("Provider Rate Limit"));
        assertTrue(report.telemetryEvidence().stream().anyMatch(e -> e.factorCode().equals("EV_HTTP_429")));
    }
}
