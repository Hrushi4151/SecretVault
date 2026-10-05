package com.secretvault.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal OpenAI-Compatible HTTP LLM Provider.
 * Connects to OpenAI, Azure OpenAI, vLLM, LocalAI, Anthropic bridges, Groq, or Mistral.
 * API keys enter exclusively via secure runtime configuration and are never logged or stored.
 */
@Component
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmProvider.class);
    private static final String PROVIDER_NAME = "OPENAI_COMPATIBLE";

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final int timeoutSeconds;
    private final double defaultTemperature;
    private final int defaultMaxTokens;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmProvider(
            @Value("${secretvault.ai.openai.base-url:${AI_BASE_URL:https://api.openai.com/v1}}") String baseUrl,
            @Value("${secretvault.ai.openai.api-key:${AI_API_KEY:}}") String apiKey,
            @Value("${secretvault.ai.openai.model:${AI_MODEL:gpt-4o-mini}}") String modelName,
            @Value("${secretvault.ai.openai.timeout-seconds:30}") int timeoutSeconds,
            @Value("${secretvault.ai.openai.temperature:0.2}") double defaultTemperature,
            @Value("${secretvault.ai.openai.max-tokens:2048}") int defaultMaxTokens,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.modelName = modelName != null && !modelName.isBlank() ? modelName.trim() : "gpt-4o-mini";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens > 0 ? defaultMaxTokens : 2048;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(5, this.timeoutSeconds)))
                .build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> payload = buildOpenAiChatPayload(request);
            String requestBody = objectMapper.writeValueAsString(payload);

            String endpoint = baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            if (!apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                log.warn("OpenAI API responded with HTTP {}: (body redacted)", response.statusCode());
                throw new IllegalStateException("OpenAI API returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("OpenAI API returned empty choices array");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode messageNode = firstChoice.path("message");
            String content = messageNode.path("content").asText("");

            int totalTokens = root.path("usage").path("total_tokens").asInt(content.length() / 4);

            List<AiToolCall> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = messageNode.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode tcNode : toolCallsNode) {
                    String callId = tcNode.path("id").asText("call_" + System.nanoTime());
                    JsonNode funcNode = tcNode.path("function");
                    String toolName = funcNode.path("name").asText();
                    String rawArgs = funcNode.path("arguments").asText("{}");
                    Map<String, Object> args = new HashMap<>();
                    try {
                        JsonNode parsedArgs = objectMapper.readTree(rawArgs);
                        if (parsedArgs.isObject()) {
                            parsedArgs.fields().forEachRemaining(entry -> {
                                if (entry.getValue().isTextual()) args.put(entry.getKey(), entry.getValue().asText());
                                else if (entry.getValue().isNumber()) args.put(entry.getKey(), entry.getValue().numberValue());
                                else if (entry.getValue().isBoolean()) args.put(entry.getKey(), entry.getValue().asBoolean());
                                else args.put(entry.getKey(), entry.getValue().toString());
                            });
                        }
                    } catch (Exception ignored) {
                    }
                    toolCalls.add(new AiToolCall(callId, toolName, args, rawArgs));
                }
            }

            return new LlmResponse(
                    content,
                    0.96,
                    totalTokens,
                    latency,
                    PROVIDER_NAME,
                    modelName,
                    toolCalls,
                    toolCalls.isEmpty()
            );
        } catch (Exception e) {
            log.error("OpenAI-compatible provider invocation failed: {}", e.getMessage());
            throw new RuntimeException("OpenAI provider failure: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildOpenAiChatPayload(LlmRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("model", modelName);
        map.put("temperature", request.temperature() > 0 ? request.temperature() : defaultTemperature);
        map.put("max_tokens", request.maxTokens() > 0 ? request.maxTokens() : defaultMaxTokens);

        List<Map<String, Object>> messages = new ArrayList<>();

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }

        if (request.conversationHistory() != null) {
            for (ChatMessage msg : request.conversationHistory()) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("role", msg.role());
                msgMap.put("content", msg.content() != null ? msg.content() : "");
                if (msg.name() != null) {
                    msgMap.put("name", msg.name());
                }
                messages.add(msgMap);
            }
        }

        if (request.userPrompt() != null && !request.userPrompt().isBlank()) {
            StringBuilder sb = new StringBuilder();
            if (request.sanitizedContextJson() != null && !request.sanitizedContextJson().equals("{}")) {
                sb.append("[Safe Context Summary: ").append(request.sanitizedContextJson()).append("]\n\n");
            }
            sb.append(request.userPrompt());
            messages.add(Map.of("role", "user", "content", sb.toString()));
        }

        map.put("messages", messages);

        if (request.availableTools() != null && !request.availableTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (var tool : request.availableTools()) {
                tools.add(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", tool.name(),
                                "description", tool.description(),
                                "parameters", tool.parameterSchema() != null ? tool.parameterSchema() : Map.of("type", "object")
                        )
                ));
            }
            map.put("tools", tools);
        }

        return map;
    }

    @Override
    public boolean isAvailable() {
        if (apiKey.isBlank()) {
            return false;
        }
        try {
            String testEndpoint = baseUrl.endsWith("/chat/completions")
                    ? baseUrl.substring(0, baseUrl.indexOf("/chat/completions")) + "/models"
                    : baseUrl + "/models";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(testEndpoint))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200 || resp.statusCode() == 404; // 404 on /models still means endpoint is up
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
