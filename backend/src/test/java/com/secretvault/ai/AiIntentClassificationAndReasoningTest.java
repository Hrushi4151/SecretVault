package com.secretvault.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.context.AiSafeContext;
import com.secretvault.ai.domain.model.AiIntentType;
import com.secretvault.ai.provider.DeterministicOfflineLlmProvider;
import com.secretvault.ai.provider.LlmRequest;
import com.secretvault.ai.provider.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 15: AI Intent Classification & Deterministic Offline Reasoning Tests")
class AiIntentClassificationAndReasoningTest {

    private DeterministicOfflineLlmProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        provider = new DeterministicOfflineLlmProvider();
        objectMapper = new ObjectMapper();
    }

    @ParameterizedTest
    @EnumSource(value = AiIntentType.class, names = {
            "COPILOT_GENERAL", "SECURITY_POSTURE", "SECURITY_FINDING", "DEPLOYMENT_RCA",
            "SYNC_FAILURE", "ROTATION_ANALYSIS", "SECRET_HEALTH", "BLAST_RADIUS",
            "REMEDIATION_RECOMMENDATION", "REMEDIATION_PLAN", "SYSTEM_HEALTH", "HELP"
    })
    @DisplayName("Deterministic offline reasoning returns high-quality structured output for each standard intent")
    void testAllStandardIntentsProvideMeaningfulResponses(AiIntentType intent) throws Exception {
        UUID workspaceId = UUID.randomUUID();
        AiSafeContext safeContext = new AiSafeContext(
                workspaceId,
                intent,
                1,
                2,
                3,
                6,
                List.of("[CRITICAL] Stale Database Root Credential"),
                "SECRET",
                "DATABASE_URL",
                "Metadata evaluation context",
                "Investigating failure traces"
        );
        String contextJson = objectMapper.writeValueAsString(safeContext);

        LlmRequest request = new LlmRequest(
                "System prompt",
                "Explain the current status and recommended action for " + intent,
                contextJson,
                0.2,
                512
        );

        LlmResponse response = provider.generate(request);

        assertNotNull(response);
        assertNotNull(response.text());
        assertFalse(response.text().isBlank());
        assertTrue(response.confidenceScore() >= 0.90, "Confidence score must be >= 0.90 for standard intents");
        assertTrue(response.tokensUsed() > 0);
        assertEquals("DETERMINISTIC_OFFLINE", response.providerName());
        assertFalse(response.text().contains("AI unavailable"), "Must never return generic 'AI unavailable'");
    }

    @Test
    @DisplayName("DEPLOYMENT_RCA: Returns identified root cause, telemetry evidence, and safe remediation action")
    void testDeploymentRcaAnalysis() throws Exception {
        AiSafeContext safeContext = new AiSafeContext(
                UUID.randomUUID(),
                AiIntentType.DEPLOYMENT_RCA,
                0, 1, 0, 1,
                List.of(), "DEPLOYMENT", "dep-checkout-v12", null, "HTTP 401 on startup"
        );
        String contextJson = objectMapper.writeValueAsString(safeContext);

        LlmRequest request = new LlmRequest(
                "System prompt",
                "Why did my production deployment fail?",
                contextJson,
                0.2,
                512
        );

        LlmResponse response = provider.generate(request);
        String text = response.text();

        assertTrue(text.contains("Root Cause Identified"));
        assertTrue(text.contains("EV_HASH_MISMATCH") || text.contains("Digest divergence"));
        assertTrue(text.contains("Recommended Safe Action"));
        assertTrue(text.contains("dual-version tolerance window"));
    }

    @Test
    @DisplayName("SECURITY_POSTURE: Incorporates findings count into posture index and 14-day trajectory")
    void testSecurityPostureAnalysis() throws Exception {
        AiSafeContext safeContext = new AiSafeContext(
                UUID.randomUUID(),
                AiIntentType.SECURITY_POSTURE,
                2, 3, 1, 6,
                List.of(), null, null, null, null
        );
        String contextJson = objectMapper.writeValueAsString(safeContext);

        LlmRequest request = new LlmRequest(
                "System prompt",
                "What security risks should I address today?",
                contextJson,
                0.2,
                512
        );

        LlmResponse response = provider.generate(request);
        String text = response.text();

        assertTrue(text.contains("Security Posture Forecast"));
        assertTrue(text.contains("Current Posture Index"));
        assertTrue(text.contains("Critical Findings") && text.contains("2"));
        assertTrue(text.contains("High Severity Findings") && text.contains("3"));
        assertTrue(text.contains("Trajectory"));
    }

    @Test
    @DisplayName("SECRET_HEALTH: Evaluates metadata status without ever revealing secret plaintext")
    void testSecretHealthMetadataEvaluation() throws Exception {
        AiSafeContext safeContext = new AiSafeContext(
                UUID.randomUUID(),
                AiIntentType.SECRET_HEALTH,
                0, 0, 0, 0,
                List.of(), "SECRET", "DATABASE_URL", null, null
        );
        String contextJson = objectMapper.writeValueAsString(safeContext);

        LlmRequest request = new LlmRequest(
                "System prompt",
                "Why is DATABASE_URL unhealthy?",
                contextJson,
                0.2,
                512
        );

        LlmResponse response = provider.generate(request);
        String text = response.text();

        assertTrue(text.contains("DATABASE_URL"));
        assertTrue(text.contains("AES-256-GCM envelope"));
        assertTrue(text.contains("Zero-Knowledge Check"));
        assertFalse(text.contains("postgres://") || text.contains("mysql://"), "Plaintext connection string must not be present");
    }
}
