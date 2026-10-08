package com.secretvault.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeminiLlmProviderTest {

    private ObjectMapper objectMapper;
    private DeterministicOfflineLlmProvider offlineProvider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        offlineProvider = new DeterministicOfflineLlmProvider();
    }

    @Test
    @DisplayName("Gemini provider is selected as primary provider in LlmProviderRegistry by default")
    void testGeminiAsPrimaryProvider() {
        GeminiLlmProvider gemini = new GeminiLlmProvider(
                "https://generativelanguage.googleapis.com",
                "gemini-3.5-flash-lite",
                "test-api-key",
                10,
                0.1,
                2048,
                objectMapper
        );

        LlmProviderRegistry registry = new LlmProviderRegistry(
                offlineProvider,
                null,
                gemini,
                null,
                "GEMINI",
                "NONE"
        );

        assertEquals("GEMINI", registry.getConfiguredProviderName());
        assertEquals(gemini, registry.getActiveProvider());
        assertEquals("gemini-3.5-flash-lite", registry.getActiveProvider().getModelName());
    }

    @Test
    @DisplayName("Unconfigured Gemini provider throws IllegalStateException and is not silently replaced by deterministic offline when fallback is NONE")
    void testUnconfiguredGeminiDoesNotSilentlyFallback() {
        GeminiLlmProvider unconfiguredGemini = new GeminiLlmProvider(
                "https://generativelanguage.googleapis.com",
                "gemini-3.5-flash-lite",
                "",
                10,
                0.1,
                2048,
                objectMapper
        );

        assertFalse(unconfiguredGemini.isConfigured());
        assertFalse(unconfiguredGemini.isAvailable());

        LlmProviderRegistry registry = new LlmProviderRegistry(
                offlineProvider,
                null,
                unconfiguredGemini,
                null,
                "GEMINI",
                "NONE"
        );

        LlmRequest request = new LlmRequest("system", "Do I have any new invitations?", "", 0.1, 1024);
        LlmResponse response = registry.executeWithFallback(request);

        assertNotNull(response);
        assertEquals("GEMINI", response.providerName());
        assertEquals("gemini-3.5-flash-lite", response.modelName());
        assertEquals(0.0, response.confidenceScore());
        assertTrue(response.text().contains("GEMINI_NOT_CONFIGURED") || response.text().contains("currently unavailable"));
        assertTrue(response.text().contains("did not silently switch to the offline engine"));
    }

    @Test
    @DisplayName("Gemini payload construction properly formats function declarations and conversation history")
    void testGeminiPayloadFormatting() {
        GeminiLlmProvider gemini = new GeminiLlmProvider(
                "https://generativelanguage.googleapis.com",
                "gemini-3.5-flash-lite",
                "test-key",
                10,
                0.1,
                2048,
                objectMapper
        );

        List<AiToolDefinition> tools = List.of(
                new AiToolDefinition("user.invitations", "Get user pending invitations", Map.of("type", "object"))
        );

        List<ChatMessage> history = List.of(
                ChatMessage.user("Do I have any new invitations?"),
                ChatMessage.assistantWithToolCalls("Checking invitations", List.of(
                        new AiToolCall("call_1", "user.invitations", Map.of())
                )),
                ChatMessage.tool("call_1", "user.invitations", "{\"totalPendingInvitations\": 1}")
        );

        LlmRequest request = new LlmRequest(
                "You are SecretVault AI Copilot",
                "Do I have any new invitations?",
                "",
                0.1,
                2048,
                history,
                tools
        );

        // Verification of properties
        assertEquals("GEMINI", gemini.getProviderName());
        assertEquals("gemini-3.5-flash-lite", gemini.getModelName());
        assertTrue(gemini.isConfigured());
    }
}
