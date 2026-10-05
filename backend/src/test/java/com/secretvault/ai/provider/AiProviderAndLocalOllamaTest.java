package com.secretvault.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderAndLocalOllamaTest {

    private ObjectMapper objectMapper;
    private DeterministicOfflineLlmProvider offlineProvider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        offlineProvider = new DeterministicOfflineLlmProvider();
    }

    @Test
    @DisplayName("Deterministic provider produces structured intelligence without external dependencies")
    void testDeterministicOfflineProvider() {
        LlmRequest req = new LlmRequest("system prompt", "Why did sync fail?", "{\"drift\": true}", 0.2, 512);
        LlmResponse res = offlineProvider.generate(req);

        assertNotNull(res);
        assertNotNull(res.text());
        assertTrue(res.confidenceScore() > 0.8);
        assertEquals("DETERMINISTIC_OFFLINE", res.providerName());
    }

    @Test
    @DisplayName("Ollama provider correctly initializes configuration and fallback on network unavailability")
    void testOllamaProviderFallback() {
        OllamaLlmProvider ollama = new OllamaLlmProvider("http://127.0.0.1:9999", "qwen3:8b", 10, 0.2, 512, objectMapper);
        assertFalse(ollama.isAvailable()); // Port 9999 is closed

        LlmProviderRegistry registry = new LlmProviderRegistry(offlineProvider, ollama, null, null, "OLLAMA", "DETERMINISTIC_OFFLINE");
        assertEquals("OLLAMA", registry.getConfiguredProviderName());

        LlmRequest req = new LlmRequest("system", "Explain encryption architecture", "", 0.2, 512);
        LlmResponse res = registry.executeWithFallback(req);

        assertNotNull(res);
        assertEquals("DETERMINISTIC_OFFLINE", res.providerName());
    }

    @Test
    @DisplayName("Gemini provider correctly initializes configuration and protects API keys")
    void testGeminiProviderConfigurationAndSecurity() {
        GeminiLlmProvider gemini = new GeminiLlmProvider(
                "https://generativelanguage.googleapis.com",
                "gemini-1.5-flash",
                "test-dummy-api-key",
                10,
                0.1,
                2048,
                objectMapper
        );

        assertEquals("GEMINI", gemini.getProviderName());
        assertEquals("gemini-1.5-flash", gemini.getModelName());
        assertTrue(gemini.isConfigured());

        // Test unconfigured instance
        GeminiLlmProvider unconfigured = new GeminiLlmProvider(
                "https://generativelanguage.googleapis.com",
                "gemini-1.5-flash",
                "",
                10,
                0.1,
                2048,
                objectMapper
        );
        assertFalse(unconfigured.isConfigured());
        assertFalse(unconfigured.isAvailable());
    }

    @Test
    @DisplayName("OpenAI-compatible provider correctly formats tools schema and protects API keys")
    void testOpenAiCompatibleProviderToolSchema() {
        OpenAiCompatibleLlmProvider openAi = new OpenAiCompatibleLlmProvider(
                "https://api.openai.com/v1",
                "test-api-key",
                "gpt-4o-mini",
                10,
                0.2,
                512,
                objectMapper
        );

        assertEquals("OPENAI_COMPATIBLE", openAi.getProviderName());
        assertEquals("gpt-4o-mini", openAi.getModelName());
        assertEquals("https://api.openai.com/v1", openAi.getBaseUrl());

        List<AiToolDefinition> tools = List.of(
                new AiToolDefinition("secret.listMetadata", "List secrets", Map.of("type", "object"))
        );

        LlmRequest req = new LlmRequest("sys", "List secrets", "", 0.2, 512, List.of(ChatMessage.user("List secrets")), tools);
        assertNotNull(req.tools());
        assertEquals(1, req.tools().size());
    }

    @Test
    @DisplayName("Provider registry accurately reflects multi-provider status list including Gemini and Ollama")
    void testProviderStatusList() {
        OllamaLlmProvider ollama = new OllamaLlmProvider("http://localhost:11434", "qwen3:8b", 10, 0.2, 512, objectMapper);
        GeminiLlmProvider gemini = new GeminiLlmProvider("https://generativelanguage.googleapis.com", "gemini-1.5-flash", "", 10, 0.1, 2048, objectMapper);
        OpenAiCompatibleLlmProvider openAi = new OpenAiCompatibleLlmProvider("https://api.groq.com/openai/v1", "secret-key", "llama3-70b-8192", 10, 0.2, 512, objectMapper);

        LlmProviderRegistry registry = new LlmProviderRegistry(offlineProvider, ollama, gemini, openAi, "OLLAMA", "DETERMINISTIC_OFFLINE");
        List<Map<String, Object>> statuses = registry.getProviderStatusList();

        assertEquals(4, statuses.size());
        assertTrue(statuses.stream().anyMatch(s -> "DETERMINISTIC_OFFLINE".equals(s.get("name"))));
        assertTrue(statuses.stream().anyMatch(s -> "OLLAMA".equals(s.get("name"))));
        assertTrue(statuses.stream().anyMatch(s -> "GEMINI".equals(s.get("name"))));
        assertTrue(statuses.stream().anyMatch(s -> "OPENAI_COMPATIBLE".equals(s.get("name"))));
    }
}
