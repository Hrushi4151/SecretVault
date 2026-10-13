package com.secretvault.ai.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeterministicOfflineLlmProvider Tests")
class DeterministicOfflineLlmProviderTest {

    private DeterministicOfflineLlmProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DeterministicOfflineLlmProvider();
    }

    @Test
    @DisplayName("Should detect deployment failure and return RCA diagnosis")
    void testDeploymentRcaPrompt() {
        LlmRequest request = new LlmRequest(
                "System prompt",
                "Why did the latest production deployment fail on payments-worker?",
                "{}",
                0.2,
                512
        );

        LlmResponse response = provider.generate(request);
        assertNotNull(response);
        assertTrue(response.text().contains("Root Cause Identified: Deployment Credential"));
        assertTrue(response.confidenceScore() >= 0.95);
        assertEquals("DETERMINISTIC_OFFLINE", response.providerName());
    }

    @Test
    @DisplayName("Should detect provider sync and drift inquiries")
    void testDriftPrompt() {
        LlmRequest request = new LlmRequest(
                "System prompt",
                "Why did AWS and Vercel drift desync on STRIPE_SECRET_KEY?",
                "{}",
                0.2,
                512
        );

        LlmResponse response = provider.generate(request);
        assertNotNull(response);
        assertTrue(response.text().contains("Provider Synchronization Drift"));
        assertTrue(response.confidenceScore() >= 0.90);
    }

    @Test
    @DisplayName("Should detect rotation staleness inquiries")
    void testRotationPrompt() {
        LlmRequest request = new LlmRequest(
                "System prompt",
                "Which credentials are stale or need rotation?",
                "{}",
                0.2,
                512
        );

        LlmResponse response = provider.generate(request);
        assertNotNull(response);
        assertTrue(response.text().contains("Secret Rotation Intelligence Analysis"));
        assertTrue(response.confidenceScore() >= 0.95);
    }

    @Test
    @DisplayName("Should detect posture forecast and trajectory inquiries")
    void testPosturePrompt() {
        LlmRequest request = new LlmRequest(
                "System prompt",
                "Show predictive security posture score and drift decay trend",
                "{}",
                0.2,
                512
        );

        LlmResponse response = provider.generate(request);
        assertNotNull(response);
        assertTrue(response.text().contains("Security Posture Forecast"));
        assertTrue(response.confidenceScore() >= 0.95);
    }
}
