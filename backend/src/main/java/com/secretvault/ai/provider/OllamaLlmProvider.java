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
 * Local / Private Cloud Ollama LLM Provider.
 * Connects to Ollama REST API for zero-cloud, fully on-premise generative AI.
 */
@Component
public class OllamaLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmProvider.class);
    private static final String PROVIDER_NAME = "OLLAMA";

    private final String baseUrl;
    private final String modelName;
    private final int timeoutSeconds;
    private final double defaultTemperature;
    private final int defaultMaxTokens;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaLlmProvider(
            @Value("${secretvault.ai.ollama.base-url:${AI_BASE_URL:http://localhost:11434}}") String baseUrl,
            @Value("${secretvault.ai.ollama.model:${AI_MODEL:llama3}}") String modelName,
            @Value("${secretvault.ai.ollama.timeout-seconds:30}") int timeoutSeconds,
            @Value("${secretvault.ai.ollama.temperature:0.2}") double defaultTemperature,
            @Value("${secretvault.ai.ollama.max-tokens:2048}") int defaultMaxTokens,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:11434";
        this.modelName = modelName != null && !modelName.isBlank() ? modelName.trim() : "llama3";
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
            Map<String, Object> payload = buildOllamaChatPayload(request);
            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                log.warn("Ollama API responded with HTTP {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("Ollama API returned HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode messageNode = root.path("message");
            String content = messageNode.path("content").asText("");
            int evalCount = root.path("eval_count").asInt(content.length() / 4);

            List<AiToolCall> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = messageNode.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode tcNode : toolCallsNode) {
                    JsonNode funcNode = tcNode.path("function");
                    String toolName = funcNode.path("name").asText();
                    Map<String, Object> args = new HashMap<>();
                    JsonNode argsNode = funcNode.path("arguments");
                    if (argsNode.isObject()) {
                        argsNode.fields().forEachRemaining(entry -> {
                            if (entry.getValue().isTextual()) args.put(entry.getKey(), entry.getValue().asText());
                            else if (entry.getValue().isNumber()) args.put(entry.getKey(), entry.getValue().numberValue());
                            else if (entry.getValue().isBoolean()) args.put(entry.getKey(), entry.getValue().asBoolean());
                            else args.put(entry.getKey(), entry.getValue().toString());
                        });
                    }
                    toolCalls.add(new AiToolCall("call_" + System.nanoTime(), toolName, args));
                }
            }

            return new LlmResponse(
                    content,
                    0.95,
                    evalCount,
                    latency,
                    PROVIDER_NAME,
                    modelName,
                    toolCalls,
                    toolCalls.isEmpty()
            );
        } catch (Exception e) {
            log.error("Ollama invocation failed against {}: {}", baseUrl, e.getMessage());
            throw new RuntimeException("Ollama provider failure: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildOllamaChatPayload(LlmRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("model", modelName);
        map.put("stream", false);

        List<Map<String, Object>> messages = new ArrayList<>();

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }

        if (request.conversationHistory() != null) {
            for (ChatMessage msg : request.conversationHistory()) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("role", msg.role());
                msgMap.put("content", msg.content() != null ? msg.content() : "");
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

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", request.temperature() > 0 ? request.temperature() : defaultTemperature);
        options.put("num_predict", request.maxTokens() > 0 ? request.maxTokens() : defaultMaxTokens);
        map.put("options", options);

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
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
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
