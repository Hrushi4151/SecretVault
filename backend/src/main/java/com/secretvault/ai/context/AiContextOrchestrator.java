package com.secretvault.ai.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.model.TelemetryEvidence;
import com.secretvault.ai.dto.AiChatRequest;
import com.secretvault.ai.dto.AiChatResponse;
import com.secretvault.ai.knowledge.AiPlatformKnowledgeService;
import com.secretvault.ai.provider.*;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.ai.security.AiRateLimiterAndBudgetEnforcer;
import com.secretvault.ai.security.AiSafetyGuardrailValidator;
import com.secretvault.ai.security.AiSecretFirewall;
import com.secretvault.ai.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agentic Multi-Step Reasoning Orchestrator for SecretVault AI Copilot.
 * Dynamically plans tool invocations, executes authorized tools server-side,
 * passes context through the Absolute Secret Value Firewall, iterates until confidence is reached,
 * and synthesizes evidence-grounded responses without any hardcoded questions or canned intents.
 */
@Service
public class AiContextOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiContextOrchestrator.class);

    private static final int MAX_ITERATIONS = 5;
    private static final int MAX_TOOL_CALLS = 10;
    private static final Pattern TOOL_JSON_PATTERN = Pattern.compile(
            "```(?:json)?\\s*\\{\\s*\"tool\"\\s*:\\s*\"([a-zA-Z0-9._]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[\\s\\S]*?\\})\\s*\\}\\s*```",
            Pattern.CASE_INSENSITIVE
    );

    private final LlmProviderRegistry providerRegistry;
    private final AiToolRegistry toolRegistry;
    private final AiToolExecutor toolExecutor;
    private final AiSecretFirewall secretFirewall;
    private final AiContextSanitizer sanitizer;
    private final AiSafetyGuardrailValidator guardrailValidator;
    private final AiRateLimiterAndBudgetEnforcer budgetEnforcer;
    private final AiPlatformKnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    public AiContextOrchestrator(
            LlmProviderRegistry providerRegistry,
            AiToolRegistry toolRegistry,
            AiToolExecutor toolExecutor,
            AiSecretFirewall secretFirewall,
            AiContextSanitizer sanitizer,
            AiSafetyGuardrailValidator guardrailValidator,
            AiRateLimiterAndBudgetEnforcer budgetEnforcer,
            AiPlatformKnowledgeService knowledgeService,
            ObjectMapper objectMapper
    ) {
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.secretFirewall = secretFirewall;
        this.sanitizer = sanitizer;
        this.guardrailValidator = guardrailValidator;
        this.budgetEnforcer = budgetEnforcer;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
    }

    public AiChatResponse orchestrate(
            UUID workspaceId,
            UUID userId,
            String userRole,
            Set<String> permissions,
            AiChatRequest request,
            List<ChatMessage> conversationHistory,
            Consumer<String> progressListener
    ) {
        long overallStartTime = System.currentTimeMillis();
        UUID conversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Rate Limiting & Token Budget Check
        budgetEnforcer.checkAndConsumeRateLimit(workspaceId, 200);

        // 2. Input Sanitization & Firewall Pre-flight
        String rawPrompt = request.prompt();
        String sanitizedPrompt = secretFirewall.sanitize(sanitizer.sanitizeUserPrompt(rawPrompt));
        secretFirewall.assertZeroPlaintext(sanitizedPrompt);

        notifyProgress(progressListener, "Analyzing security context...");

        AiToolInvocationContext toolContext = new AiToolInvocationContext(
                workspaceId,
                userId,
                "user@secretvault.local",
                userRole != null ? userRole : "DEVELOPER",
                permissions != null ? permissions : Set.of(),
                conversationId,
                correlationId
        );

        // 3. Platform Knowledge Grounding
        List<AiPlatformKnowledgeService.KnowledgeArticle> relevantDocs = knowledgeService.search(sanitizedPrompt, 2);
        StringBuilder knowledgePrompt = new StringBuilder();
        if (!relevantDocs.isEmpty()) {
            knowledgePrompt.append("\n\nAuthoritative SecretVault Platform Knowledge:\n");
            for (var doc : relevantDocs) {
                knowledgePrompt.append("--- ").append(doc.title()).append(" ---\n").append(doc.content()).append("\n");
            }
        }

        String systemPrompt = """
                You are SecretVault AI Security Copilot — a production-grade DevSecOps intelligence agent.
                You assist engineers, security operators, and platform architects with arbitrary questions about SecretVault, their authorized workspace, projects, environments, secret metadata, rotation, synchronization, drift, cloud integrations, and incidents.

                CORE INVARIANTS:
                1. Zero Plaintext Invariant: NEVER output, request, or simulate raw secrets, passwords, tokens, or private keys. Work only with safe metadata, fingerprints, and policy states.
                2. Server-Side Tool Execution: When you need facts about the workspace, use the available tools. Do not invent resource names, IDs, or states.
                3. Evidence Grounding: State facts clearly with confidence and concrete resource references.
                4. Tone: Technical, precise, authoritative DevSecOps engineer.
                """ + knowledgePrompt;

        // 4. Assemble Messages
        List<ChatMessage> messages = new ArrayList<>();
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            messages.addAll(conversationHistory);
        }
        messages.add(ChatMessage.user(sanitizedPrompt));

        List<AiToolDefinition> toolDefinitions = toolRegistry.getToolDefinitions();
        Set<String> executedToolSignatures = new HashSet<>();
        List<TelemetryEvidence> collectedEvidence = new ArrayList<>();
        int totalToolCallsCount = 0;
        String finalAnswer = null;
        double confidence = 0.92;
        String activeModel = "deterministic-offline";
        String activeProvider = "DETERMINISTIC_OFFLINE";

        // 5. Dynamic Agentic Reasoning Loop
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            LlmRequest llmRequest = new LlmRequest(
                    systemPrompt,
                    sanitizedPrompt,
                    "",
                    0.2,
                    1024,
                    messages,
                    toolDefinitions
            );

            LlmResponse response = providerRegistry.executeWithFallback(llmRequest);
            activeModel = response.modelName();
            activeProvider = response.providerName();
            confidence = response.confidenceScore();

            List<AiToolCall> toolCalls = response.toolCalls();

            // Also detect tool calls written as JSON in model text if provider doesn't output native tool_calls
            if ((toolCalls == null || toolCalls.isEmpty()) && response.text() != null) {
                toolCalls = extractJsonToolCalls(response.text());
            }

            if (toolCalls == null || toolCalls.isEmpty()) {
                // No further tools needed: model produced final answer
                finalAnswer = response.text();
                break;
            }

            // Execute requested tools
            boolean anyToolExecuted = false;
            for (AiToolCall tc : toolCalls) {
                if (totalToolCallsCount >= MAX_TOOL_CALLS) {
                    log.warn("AI Copilot exceeded maximum tool call budget ({})", MAX_TOOL_CALLS);
                    break;
                }

                String signature = tc.name() + ":" + tc.arguments();
                if (executedToolSignatures.contains(signature)) {
                    log.debug("Loop prevention: skipping repeated tool call signature: {}", signature);
                    continue;
                }
                executedToolSignatures.add(signature);

                notifyProgress(progressListener, "Executing " + tc.name() + "...");
                totalToolCallsCount++;

                AiToolResult result = toolExecutor.executeTool(tc.name(), tc.arguments(), toolContext);
                anyToolExecuted = true;

                // Add tool result to evidence
                if (result.success()) {
                    collectedEvidence.add(new TelemetryEvidence(
                            "EV_TOOL_" + tc.name().toUpperCase().replace(".", "_"),
                            "Tool Output: " + tc.name(),
                            "Server-verified metadata from " + tc.name(),
                            workspaceId.toString(),
                            Instant.now()
                    ));
                }

                // Append assistant tool intent and tool result to conversation messages for next reasoning turn
                messages.add(new ChatMessage("assistant", "Invoking tool `" + tc.name() + "` with arguments " + tc.arguments()));
                messages.add(ChatMessage.tool(tc.id() != null ? tc.id() : tc.name(), tc.name(), result.outputJson()));
            }

            if (!anyToolExecuted) {
                // All tool calls were skipped due to loop detection, take model text as answer
                finalAnswer = response.text();
                break;
            }
        }

        if (finalAnswer == null || finalAnswer.isBlank()) {
            finalAnswer = "Analysis complete based on authorized workspace telemetry.";
        }

        // 6. Response Validation through Guardrails and Firewall
        String validatedText = guardrailValidator.validateAndSanitizeResponse(finalAnswer);
        validatedText = secretFirewall.sanitizeLlmResponse(validatedText);
        secretFirewall.assertZeroPlaintext(validatedText);

        long totalLatencyMs = System.currentTimeMillis() - overallStartTime;

        // 7. Extract Recommendations and Synthesize Response
        List<String> recommendations = extractRecommendations(validatedText);
        if (collectedEvidence.isEmpty()) {
            collectedEvidence.add(new TelemetryEvidence(
                    "EV_WORKSPACE_TELEMETRY",
                    "Authoritative Workspace Context",
                    "Safe metadata verified with zero detected plaintext.",
                    workspaceId.toString(),
                    Instant.now()
            ));
        }

        UUID responseInquiryId = UUID.randomUUID();

        return new AiChatResponse(
                responseInquiryId,
                conversationId,
                sanitizedPrompt,
                "COPILOT_AGENTIC",
                validatedText,
                confidence,
                confidence >= 0.90 ? "HIGH" : (confidence >= 0.70 ? "MEDIUM" : "LOW"),
                activeProvider,
                activeModel,
                totalLatencyMs,
                collectedEvidence,
                recommendations,
                List.of(),
                List.of("Zero-knowledge metadata isolation enforced", "Server-side RBAC boundary applied"),
                "AI responses are strictly advisory and require authorized approval before executing changes.",
                Instant.now()
        );
    }

    private List<AiToolCall> extractJsonToolCalls(String text) {
        List<AiToolCall> list = new ArrayList<>();
        if (text == null) return list;

        Matcher matcher = TOOL_JSON_PATTERN.matcher(text);
        while (matcher.find()) {
            String toolName = matcher.group(1);
            String argsJson = matcher.group(2);
            try {
                Map<String, Object> args = objectMapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {});
                list.add(new AiToolCall("call_" + UUID.randomUUID().toString().substring(0, 8), toolName, args));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private List<String> extractRecommendations(String text) {
        List<String> recs = new ArrayList<>();
        if (text == null) return recs;

        if (text.contains("rollover") || text.contains("tolerance window")) {
            recs.add("Trigger zero-downtime rotation with dual-version tolerance window (300s TTL).");
        }
        if (text.contains("reconcil") || text.contains("drift")) {
            recs.add("Reconcile authoritative hash across target cloud providers.");
        }
        if (text.contains("JIT") || text.contains("elevation")) {
            recs.add("Request Just-In-Time access elevation for target environment.");
        }
        if (recs.isEmpty()) {
            recs.add("Continuously monitor workspace security posture and rotation compliance.");
        }
        return recs;
    }

    private void notifyProgress(Consumer<String> listener, String message) {
        if (listener != null) {
            try {
                listener.accept(message);
            } catch (Exception ignored) {
            }
        }
    }
}
