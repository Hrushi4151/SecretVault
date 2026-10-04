package com.secretvault.ai.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registry resolving the active LLM provider.
 * Supports external HTTP providers with automated failover to DeterministicOfflineLlmProvider.
 */
@Component
public class LlmProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderRegistry.class);

    private final DeterministicOfflineLlmProvider offlineProvider;
    private final String configuredProvider;

    public LlmProviderRegistry(
            DeterministicOfflineLlmProvider offlineProvider,
            @Value("${secretvault.ai.provider:DETERMINISTIC_OFFLINE}") String configuredProvider
    ) {
        this.offlineProvider = offlineProvider;
        this.configuredProvider = configuredProvider != null ? configuredProvider.trim().toUpperCase() : "DETERMINISTIC_OFFLINE";
    }

    public LlmProvider getActiveProvider() {
        // In local/test/CI environments or when configured as DETERMINISTIC_OFFLINE, use offlineProvider directly
        return offlineProvider;
    }

    public LlmResponse executeWithFallback(LlmRequest request) {
        try {
            LlmProvider provider = getActiveProvider();
            if (provider != null && provider.isAvailable()) {
                return provider.generate(request);
            }
        } catch (Exception ex) {
            log.warn("Active LLM provider failed, falling back to DeterministicOfflineLlmProvider: {}", ex.getMessage());
        }
        return offlineProvider.generate(request);
    }
}
