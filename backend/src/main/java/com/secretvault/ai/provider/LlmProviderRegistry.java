package com.secretvault.ai.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry resolving and managing active and fallback LLM providers.
 * Supports Ollama, OpenAI-compatible APIs, and Deterministic Offline reasoning.
 */
@Component
public class LlmProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderRegistry.class);

    private final DeterministicOfflineLlmProvider offlineProvider;
    private final OllamaLlmProvider ollamaProvider;
    private final GeminiLlmProvider geminiProvider;
    private final OpenAiCompatibleLlmProvider openAiProvider;
    private final String configuredProviderName;
    private final String fallbackProviderName;

    @Autowired
    public LlmProviderRegistry(
            DeterministicOfflineLlmProvider offlineProvider,
            @Autowired(required = false) OllamaLlmProvider ollamaProvider,
            @Autowired(required = false) GeminiLlmProvider geminiProvider,
            @Autowired(required = false) OpenAiCompatibleLlmProvider openAiProvider,
            @Value("${secretvault.ai.provider:${AI_PROVIDER:OLLAMA}}") String configuredProviderName,
            @Value("${secretvault.ai.fallback-provider:${AI_FALLBACK_PROVIDER:DETERMINISTIC_OFFLINE}}") String fallbackProviderName
    ) {
        this.offlineProvider = offlineProvider;
        this.ollamaProvider = ollamaProvider;
        this.geminiProvider = geminiProvider;
        this.openAiProvider = openAiProvider;
        this.configuredProviderName = configuredProviderName != null ? configuredProviderName.trim().toUpperCase() : "OLLAMA";
        this.fallbackProviderName = fallbackProviderName != null ? fallbackProviderName.trim().toUpperCase() : "DETERMINISTIC_OFFLINE";
    }

    public LlmProviderRegistry(DeterministicOfflineLlmProvider offlineProvider, String configuredProviderName) {
        this(offlineProvider, null, null, null, configuredProviderName, "DETERMINISTIC_OFFLINE");
    }

    public LlmProviderRegistry(DeterministicOfflineLlmProvider offlineProvider, OllamaLlmProvider ollamaProvider, OpenAiCompatibleLlmProvider openAiProvider) {
        this(offlineProvider, ollamaProvider, null, openAiProvider, "OLLAMA", "DETERMINISTIC_OFFLINE");
    }

    public LlmProvider getActiveProvider() {
        LlmProvider primary = resolveProviderByName(configuredProviderName);
        if (primary != null && primary.isAvailable()) {
            return primary;
        }

        LlmProvider fallback = resolveProviderByName(fallbackProviderName);
        if (fallback != null && fallback.isAvailable()) {
            return fallback;
        }

        // Check availability if configured is not offline
        if (ollamaProvider != null && ollamaProvider.isAvailable()) {
            return ollamaProvider;
        }
        if (geminiProvider != null && geminiProvider.isAvailable()) {
            return geminiProvider;
        }
        if (openAiProvider != null && openAiProvider.isAvailable()) {
            return openAiProvider;
        }

        return offlineProvider;
    }

    private LlmProvider resolveProviderByName(String name) {
        if (name == null) return null;
        String upper = name.toUpperCase();
        if (upper.contains("OLLAMA") || upper.contains("QWEN") || upper.contains("LOCAL")) {
            return ollamaProvider;
        }
        if (upper.contains("GEMINI")) {
            return geminiProvider;
        }
        if (upper.contains("OPENAI") || upper.contains("GPT")) {
            return openAiProvider;
        }
        if (upper.contains("DETERMINISTIC") || upper.contains("OFFLINE")) {
            return offlineProvider;
        }
        return null;
    }

    public LlmResponse executeWithFallback(LlmRequest request) {
        LlmProvider primary = getActiveProvider();
        if (primary != offlineProvider) {
            try {
                return primary.generate(request);
            } catch (Exception ex) {
                log.warn("Primary LLM provider [{}] failed: {}. Attempting fallback...",
                        primary.getProviderName(), ex.getMessage());

                LlmProvider fallback = resolveProviderByName(fallbackProviderName);
                if (fallback != null && fallback != primary && fallback.isAvailable()) {
                    try {
                        return fallback.generate(request);
                    } catch (Exception fallbackEx) {
                        log.warn("Fallback LLM provider [{}] also failed: {}. Activating deterministic offline fallback.",
                                fallback.getProviderName(), fallbackEx.getMessage());
                    }
                }
            }
        }
        return offlineProvider.generate(request);
    }

    public List<Map<String, Object>> getProviderStatusList() {
        List<Map<String, Object>> list = new ArrayList<>();
        LlmProvider active = getActiveProvider();

        list.add(Map.of(
                "name", "DETERMINISTIC_OFFLINE",
                "model", offlineProvider.getModelName(),
                "available", offlineProvider.isAvailable(),
                "isActive", active == offlineProvider,
                "type", "LOCAL_OFFLINE"
        ));

        if (ollamaProvider != null) {
            Map<String, Object> ollamaMap = new HashMap<>();
            ollamaMap.put("name", "OLLAMA");
            ollamaMap.put("model", ollamaProvider.getModelName());
            ollamaMap.put("baseUrl", ollamaProvider.getBaseUrl());
            ollamaMap.put("available", ollamaProvider.isAvailable());
            ollamaMap.put("isActive", active == ollamaProvider);
            ollamaMap.put("type", "LOCAL_DAEMON");
            ollamaMap.put("installedModels", ollamaProvider.discoverInstalledModels());
            list.add(ollamaMap);
        }

        if (geminiProvider != null) {
            Map<String, Object> geminiMap = new HashMap<>();
            geminiMap.put("name", "GEMINI");
            geminiMap.put("model", geminiProvider.getModelName());
            geminiMap.put("available", geminiProvider.isAvailable());
            geminiMap.put("isActive", active == geminiProvider);
            geminiMap.put("configured", geminiProvider.isConfigured());
            geminiMap.put("type", "CLOUD_API");
            list.add(geminiMap);
        }

        if (openAiProvider != null) {
            Map<String, Object> openAiMap = new HashMap<>();
            openAiMap.put("name", "OPENAI_COMPATIBLE");
            openAiMap.put("model", openAiProvider.getModelName());
            openAiMap.put("baseUrl", openAiProvider.getBaseUrl());
            openAiMap.put("available", openAiProvider.isAvailable());
            openAiMap.put("isActive", active == openAiProvider);
            openAiMap.put("type", "OPENAI_COMPATIBLE");
            list.add(openAiMap);
        }

        return list;
    }

    public String getConfiguredProviderName() {
        return configuredProviderName;
    }
}
