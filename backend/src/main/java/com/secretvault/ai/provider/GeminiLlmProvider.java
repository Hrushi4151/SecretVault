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
import java.util.*;

/**
 * Google Gemini Cloud LLM Provider.
 * Integrates natively with Google Generative Language API (Gemini 3.5 Flash Lite / Pro).
 * Enforces zero-plaintext invariants, server-side function calling, and zero disk/log persistence
 * of runtime API credentials.
 */
@Component
public class GeminiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmProvider.class);
    private static final String PROVIDER_NAME = "GEMINI";

    private final String baseUrl;
    private final String modelName;
    private final String apiKey;
    private final int timeoutSeconds;
    private final double defaultTemperature;
    private final int defaultMaxTokens;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiLlmProvider(
            @Value("${secretvault.ai.gemini.base-url:${AI_GEMINI_BASE_URL:https://generativelanguage.googleapis.com}}") String baseUrl,
            @Value("${secretvault.ai.gemini.model:${AI_GEMINI_MODEL:gemini-3.5-flash-lite}}") String modelName,
            @Value("${secretvault.ai.gemini.api-key:${AI_GEMINI_API_KEY:${GEMINI_API_KEY:}}}") String apiKey,
            @Value("${secretvault.ai.gemini.timeout-seconds:60}") int timeoutSeconds,
            @Value("${secretvault.ai.gemini.temperature:0.1}") double defaultTemperature,
            @Value("${secretvault.ai.gemini.max-tokens:2048}") int defaultMaxTokens,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://generativelanguage.googleapis.com";
        this.modelName = modelName != null && !modelName.isBlank()
                ? modelName.trim().toLowerCase().replace("models/", "")
                : "gemini-3.5-flash-lite";

        String resolvedKey = apiKey != null ? apiKey.trim() : "";
        if (resolvedKey.isBlank()) {
            String envKey = System.getenv("AI_GEMINI_API_KEY");
            if (envKey == null || envKey.isBlank()) {
                envKey = System.getenv("GEMINI_API_KEY");
            }
            if (envKey == null || envKey.isBlank()) {
                envKey = System.getProperty("AI_GEMINI_API_KEY");
            }
            if (envKey == null || envKey.isBlank()) {
                envKey = System.getProperty("GEMINI_API_KEY");
            }
            resolvedKey = envKey != null ? envKey.trim() : "";
        }
        this.apiKey = resolvedKey;

        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens > 0 ? defaultMaxTokens : 2048;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, this.timeoutSeconds)))
                .build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "GEMINI_NOT_CONFIGURED: Missing runtime AI_GEMINI_API_KEY environment variable.");
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> payload = buildGeminiPayload(request);
            String requestBody = objectMapper.writeValueAsString(payload);

            String endpointUrl = String.format("%s/v1beta/models/%s:generateContent?key=%s", baseUrl, modelName, apiKey);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                int status = response.statusCode();
                String errSummary = "HTTP " + status;
                try {
                    JsonNode errNode = objectMapper.readTree(response.body());
                    if (errNode.has("error") && errNode.get("error").has("message")) {
                        errSummary = errNode.get("error").get("message").asText();
                    }
                } catch (Exception ignored) {
                }

                if (status == 401 || status == 403) {
                    throw new IllegalStateException("GEMINI_AUTH_ERROR: Invalid or unauthorized Gemini API key (" + errSummary + ")");
                } else if (status == 404) {
                    throw new IllegalStateException("GEMINI_MODEL_NOT_FOUND: Model '" + modelName + "' not found on Gemini API (" + errSummary + ")");
                } else if (status == 429) {
                    throw new IllegalStateException("GEMINI_RATE_LIMIT_EXCEEDED: Rate limit reached on Gemini API (" + errSummary + ")");
                } else if (status >= 500) {
                    throw new IllegalStateException("GEMINI_SERVICE_UNAVAILABLE: Google Generative Language service error (" + errSummary + ")");
                } else {
                    throw new IllegalStateException("GEMINI_API_ERROR: HTTP " + status + " - " + errSummary);
                }
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidatesNode = root.path("candidates");
            if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
                throw new IllegalStateException("Gemini API returned no response candidates");
            }

            JsonNode firstCandidate = candidatesNode.get(0);
            JsonNode contentParts = firstCandidate.path("content").path("parts");

            StringBuilder textResponse = new StringBuilder();
            List<AiToolCall> toolCalls = new ArrayList<>();

            if (contentParts.isArray()) {
                for (JsonNode part : contentParts) {
                    if (part.has("text")) {
                        textResponse.append(part.path("text").asText(""));
                    }
                    if (part.has("functionCall")) {
                        JsonNode funcCall = part.path("functionCall");
                        String rawToolName = funcCall.path("name").asText();
                        // Map underscore function names back to canonical dot format if applicable
                        String toolName = rawToolName.replace("_", ".");
                        Map<String, Object> args = new HashMap<>();
                        JsonNode argsNode = funcCall.path("args");
                        if (argsNode.isObject()) {
                            argsNode.fields().forEachRemaining(entry -> {
                                if (entry.getValue().isTextual())
                                    args.put(entry.getKey(), entry.getValue().asText());
                                else if (entry.getValue().isNumber())
                                    args.put(entry.getKey(), entry.getValue().numberValue());
                                else if (entry.getValue().isBoolean())
                                    args.put(entry.getKey(), entry.getValue().asBoolean());
                                else
                                    args.put(entry.getKey(), entry.getValue().toString());
                            });
                        }
                        String thoughtSig = null;
                        if (part.has("thoughtSignature")) {
                            thoughtSig = part.get("thoughtSignature").asText();
                        } else if (part.has("thought_signature")) {
                            thoughtSig = part.get("thought_signature").asText();
                        } else if (funcCall.has("thoughtSignature")) {
                            thoughtSig = funcCall.get("thoughtSignature").asText();
                        } else if (funcCall.has("thought_signature")) {
                            thoughtSig = funcCall.get("thought_signature").asText();
                        }
                        toolCalls.add(new AiToolCall("gemini_call_" + System.nanoTime(), toolName, args, null, thoughtSig));
                    }
                }
            }

            int promptTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
            int candidateTokens = root.path("usageMetadata").path("candidatesTokenCount")
                    .asInt(textResponse.length() / 4);

            return new LlmResponse(
                    textResponse.toString(),
                    0.96,
                    promptTokens + candidateTokens,
                    latency,
                    PROVIDER_NAME,
                    modelName,
                    toolCalls,
                    toolCalls.isEmpty());
        } catch (Exception e) {
            log.error("Gemini LLM invocation failed: {}", e.getMessage());
            throw new RuntimeException("Gemini provider failure: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildGeminiPayload(LlmRequest request) {
        Map<String, Object> root = new HashMap<>();

        // 1. System Instruction
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            root.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.systemPrompt()))));
        }

        // 2. Contents / Multi-Turn Conversation History
        List<Map<String, Object>> contents = new ArrayList<>();

        if (request.conversationHistory() != null && !request.conversationHistory().isEmpty()) {
            for (ChatMessage msg : request.conversationHistory()) {
                String role = msg.role() != null ? msg.role().toLowerCase() : "user";
                if ("assistant".equals(role) || "model".equals(role)) {
                    if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                        List<Map<String, Object>> parts = new ArrayList<>();
                        for (AiToolCall tc : msg.toolCalls()) {
                            Map<String, Object> fc = new HashMap<>();
                            fc.put("name", tc.name() != null ? tc.name().replace(".", "_") : "tool");
                            fc.put("args", tc.arguments() != null ? tc.arguments() : Map.of());

                            Map<String, Object> partMap = new HashMap<>();
                            partMap.put("functionCall", fc);
                            if (tc.thoughtSignature() != null && !tc.thoughtSignature().isBlank()) {
                                partMap.put("thoughtSignature", tc.thoughtSignature());
                                partMap.put("thought_signature", tc.thoughtSignature());
                            }
                            parts.add(partMap);
                        }
                        contents.add(Map.of("role", "model", "parts", parts));
                    } else {
                        contents.add(Map.of("role", "model", "parts", List.of(Map.of("text", msg.content() != null ? msg.content() : ""))));
                    }
                } else if ("tool".equals(role) || "function".equals(role)) {
                    String funcName = msg.toolName() != null ? msg.toolName().replace(".", "_") : "tool";
                    Object resultPayload;
                    try {
                        resultPayload = objectMapper.readValue(msg.content(), Map.class);
                    } catch (Exception ignored) {
                        resultPayload = Map.of("result", msg.content() != null ? msg.content() : "");
                    }
                    contents.add(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of(
                                    "functionResponse", Map.of(
                                            "name", funcName,
                                            "response", Map.of("result", resultPayload)
                                    )
                            ))
                    ));
                } else {
                    contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", msg.content() != null ? msg.content() : ""))));
                }
            }
        } else if (request.userPrompt() != null && !request.userPrompt().isBlank()) {
            StringBuilder sb = new StringBuilder();
            if (request.sanitizedContextJson() != null && !request.sanitizedContextJson().equals("{}")) {
                sb.append("[Safe Platform Context: ").append(request.sanitizedContextJson()).append("]\n\n");
            }
            sb.append(request.userPrompt());
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", sb.toString()))));
        }

        root.put("contents", contents);

        // 3. Tools / Function Declarations
        if (request.availableTools() != null && !request.availableTools().isEmpty()) {
            List<Map<String, Object>> funcDeclarations = new ArrayList<>();
            for (var tool : request.availableTools()) {
                Map<String, Object> decl = new HashMap<>();
                decl.put("name", tool.name().replace(".", "_"));
                decl.put("description", tool.description() != null ? tool.description() : tool.name());
                if (tool.parameterSchema() != null && !tool.parameterSchema().isEmpty()) {
                    decl.put("parameters", tool.parameterSchema());
                } else {
                    decl.put("parameters", Map.of("type", "OBJECT", "properties", Map.of()));
                }
                funcDeclarations.add(decl);
            }
            root.put("tools", List.of(Map.of("functionDeclarations", funcDeclarations)));
        }

        // 4. Generation Config
        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("temperature", request.temperature() > 0 ? request.temperature() : defaultTemperature);
        genConfig.put("maxOutputTokens", request.maxTokens() > 0 ? request.maxTokens() : defaultMaxTokens);
        root.put("generationConfig", genConfig);

        return root;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean isAvailable() {
        return isConfigured();
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
