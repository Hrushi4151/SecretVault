package com.secretvault.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secretvault.ai.context.AiContextBuilder;
import com.secretvault.ai.context.AiSafeContext;
import com.secretvault.ai.domain.model.AiIntentType;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AiContextBuilder Bounded Context & Zero-Plaintext Tests")
class AiContextBuilderAndSizeBoundTest {

    private SecurityFindingRepository findingRepository;
    private AiContextSanitizer sanitizer;
    private ObjectMapper objectMapper;
    private AiContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        findingRepository = mock(SecurityFindingRepository.class);
        sanitizer = new AiContextSanitizer();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        contextBuilder = new AiContextBuilder(findingRepository, sanitizer, objectMapper);
    }

    @Test
    @DisplayName("Bounds operational context hint to maximum 1000 characters with truncation marker")
    void testContextTruncationOfOversizedHints() {
        UUID workspaceId = UUID.randomUUID();
        String oversizedHint = "A".repeat(1500);

        AiSafeContext context = contextBuilder.buildSafeContext(
                workspaceId,
                AiIntentType.DEPLOYMENT_RCA,
                "DEPLOYMENT",
                "dep-worker-99",
                oversizedHint
        );

        assertNotNull(context);
        assertTrue(context.sanitizedOperationalHint().length() <= 1020);
        assertTrue(context.sanitizedOperationalHint().endsWith("... [truncated]"));
        assertEquals(1000, context.sanitizedOperationalHint().substring(0, context.sanitizedOperationalHint().indexOf("... [truncated]")).length());
    }

    @Test
    @DisplayName("Bounds top findings list in context to at most 5 items regardless of repository count")
    void testMaxFindingsBounds() {
        UUID workspaceId = UUID.randomUUID();
        List<FindingStatus> openStatuses = List.of(FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED);

        List<SecurityFinding> findings = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            SecurityFinding f = new SecurityFinding(
                    workspaceId,
                    null,
                    null,
                    FindingCategory.REPOSITORY_SECRET_EXPOSURE,
                    i <= 3 ? FindingSeverity.CRITICAL : FindingSeverity.HIGH,
                    FindingConfidence.HIGH,
                    "Finding #" + i,
                    "Description for finding #" + i,
                    "Remediation for finding #" + i,
                    "{}",
                    "fp-" + i
            );
            findings.add(f);
        }

        when(findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(eq(workspaceId), eq(FindingSeverity.CRITICAL), any())).thenReturn(3L);
        when(findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(eq(workspaceId), eq(FindingSeverity.HIGH), any())).thenReturn(17L);
        when(findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(eq(workspaceId), eq(FindingSeverity.MEDIUM), any())).thenReturn(0L);
        when(findingRepository.countByWorkspaceIdAndStatusIn(eq(workspaceId), any())).thenReturn(20L);
        when(findingRepository.findByWorkspaceIdAndStatusIn(eq(workspaceId), any())).thenReturn(findings);

        AiSafeContext context = contextBuilder.buildSafeContext(
                workspaceId,
                AiIntentType.SECURITY_POSTURE,
                "WORKSPACE",
                workspaceId.toString(),
                "Analyze posture"
        );

        assertNotNull(context);
        assertEquals(3L, context.openCriticalFindingsCount());
        assertEquals(17L, context.openHighFindingsCount());
        assertEquals(20L, context.totalOpenFindingsCount());
        assertEquals(5, context.topFindingsSummary().size());
        assertTrue(context.topFindingsSummary().get(0).contains("Finding #1"));
        assertTrue(context.topFindingsSummary().get(4).contains("Finding #5"));
    }

    @Test
    @DisplayName("Sanitizes credential patterns in hints and verifies zero-plaintext compliance")
    void testSanitizesCredentialsAndEnforcesZeroPlaintext() {
        UUID workspaceId = UUID.randomUUID();
        String dangerousHint = "Deployment failed due to auth token ghp_1234567890abcdef1234567890abcdef1234 and AWS key AKIAIOSFODNN7EXAMPLE";

        AiSafeContext context = contextBuilder.buildSafeContext(
                workspaceId,
                AiIntentType.DEPLOYMENT_RCA,
                "DEPLOYMENT",
                "dep-01",
                dangerousHint
        );

        assertNotNull(context);
        assertFalse(context.sanitizedOperationalHint().contains("ghp_1234567890abcdef1234567890abcdef1234"));
        assertFalse(context.sanitizedOperationalHint().contains("AKIAIOSFODNN7EXAMPLE"));
        assertTrue(context.sanitizedOperationalHint().contains("[REDACTED_SECRET_TOKEN]"));
        assertTrue(context.zeroPlaintextEnforced());

        // Context serialization must also pass zero plaintext assertion
        String serialized = contextBuilder.serializeContext(context);
        assertDoesNotThrow(() -> sanitizer.assertZeroPlaintext(serialized));
    }

    @Test
    @DisplayName("Handles null and empty repository states gracefully without exceptions")
    void testHandlesNullAndEmptyRepository() {
        AiContextBuilder nullRepoBuilder = new AiContextBuilder(null, sanitizer, objectMapper);
        UUID workspaceId = UUID.randomUUID();

        AiSafeContext context = nullRepoBuilder.buildSafeContext(
                workspaceId,
                AiIntentType.COPILOT_GENERAL,
                null,
                null,
                null
        );

        assertNotNull(context);
        assertEquals(0, context.openCriticalFindingsCount());
        assertEquals(0, context.totalOpenFindingsCount());
        assertTrue(context.topFindingsSummary().isEmpty());
        assertEquals("", context.sanitizedOperationalHint());
    }
}
