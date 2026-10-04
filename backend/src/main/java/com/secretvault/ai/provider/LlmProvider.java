package com.secretvault.ai.provider;

/**
 * SPI for Pluggable LLM Providers.
 */
public interface LlmProvider {

    LlmResponse generate(LlmRequest request);

    boolean isAvailable();

    String getProviderName();

    String getModelName();
}
