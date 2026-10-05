package com.secretvault.ai.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private final OpenAiCompatibleLlmProvider openAiProvider;
    private final String configuredProviderName;
    private final String fallbackProviderName;

    @Autowired
    public LlmProviderRegistry(
            DeterministicOfflineLlmProvider offlineProvider,
            @Autowired(required = false) OllamaLlmProvider ollamaProvider,
            @Autowired(required = false) OpenAiCompatibleLlmProvider openAiProvider,
            @Value("${secretvault.ai.provider:${AI_PROVIDER:DETERMINISTIC_OFFLINE}}") String configuredProviderName,
            @Value("${secretvault.ai.fallback-provider:${AI_FALLBACK_PROVIDER:DETERMINISTIC_OFFLINE}}") String fallbackProviderName
    ) {
        this.offlineProvider = offlineProvider;
        this.ollamaProvider = ollamaProvider;
        this.openAiProvider = openAiProvider;
        this.configuredProviderName = configuredProviderName != null ? configuredProviderName.trim().toUpperCase() : "DETERMINISTIC_OFFLINE";
        this.fallbackProviderName = fallbackProviderName != null ? fallbackProviderName.trim().toUpperCase() : "DETERMINISTIC_OFFLINE";
    }

    public LlmProviderRegistry(DeterministicOfflineLlmProvider offlineProvider, String configuredProviderName) {
        this(offlineProvider, null, null, configuredProviderName, "DETERMINISTIC_OFFLINE");
    }

    public LlmProviderRegistry(DeterministicOfflineLlmProvider offlineProvider, OllamaLlmProvider ollamaProvider, OpenAiCompatibleLlmProvider openAiProvider) {
        this(offlineProvider, ollamaProvider, openAiProvider, "DETERMINISTIC_OFFLINE", "DETERMINISTIC_OFFLINE");
    }

    public LlmProvider getActiveProvider() {
        String target = configuredProviderName;
        if (target.contains("OLLAMA") && ollamaProvider != null && ollamaProvider.isAvailable()) {
            return ollamaProvider;
        }
        if ((target.contains("OPENAI") || target.contains("GPT")) && openAiProvider != null && openAiProvider.isAvailable()) {
            return openAiProvider;
        }
        if (target.contains("DETERMINISTIC") || target.contains("OFFLINE")) {
            return offlineProvider;
        }

        // Check availability if configured is not offline
        if (target.contains("OLLAMA") && ollamaProvider != null) {
            return ollamaProvider;
        }
        if ((target.contains("OPENAI") || target.contains("GPT")) && openAiProvider != null) {
            return openAiProvider;
        }

        return offlineProvider;
    }

    public LlmResponse executeWithFallback(LlmRequest request) {
        LlmProvider primary = getActiveProvider();
        if (primary != offlineProvider) {
            try {
                return primary.generate(request);
            } catch (Exception ex) {
                log.warn("Primary LLM provider [{}] failed: {}. Activating deterministic offline fallback.",
                        primary.getProviderName(), ex.getMessage());
            }
        }
        return offlineProvider.generate(request);
    }

    public List<Map<String, Object>> getProviderStatusList() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of(
                "name", "DETERMINISTIC_OFFLINE",
                "model", offlineProvider.getModelName(),
                "available", offlineProvider.isAvailable(),
                "isActive", getActiveProvider() == offlineProvider
        ));
        if (ollamaProvider != null) {
            list.add(Map.of(
                    "name", "OLLAMA",
                    "model", ollamaProvider.getModelName(),
                    "baseUrl", ollamaProvider.getBaseUrl(),
                    "available", ollamaProvider.isAvailable(),
                    "isActive", getActiveProvider() == ollamaProvider
            ));
        }
        if (openAiProvider != null) {
            list.add(Map.of(
                    "name", "OPENAI_COMPATIBLE",
                    "model", openAiProvider.getModelName(),
                    "baseUrl", openAiProvider.getBaseUrl(),
                    "available", openAiProvider.isAvailable(),
                    "isActive", getActiveProvider() == openAiProvider
            ));
        }
        return list;
    }

    public String getConfiguredProviderName() {
        return configuredProviderName;
    }
}
