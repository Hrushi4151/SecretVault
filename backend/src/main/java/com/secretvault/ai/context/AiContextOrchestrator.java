package com.secretvault.ai.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.model.TelemetryEvidence;
import com.secretvault.ai.dto.AiActionProposal;
import com.secretvault.ai.dto.AiChatRequest;
import com.secretvault.ai.dto.AiChatResponse;
import com.secretvault.ai.dto.AiResourceReference;
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
 * Operates across THREE dynamic modes (Chatbot, Security / System Analyst, Agent) without requiring manual mode switching.
 * Dynamically plans tool invocations, executes authorized tools server-side,
 * enforces Zero-Plaintext Invariant, resolves server-authoritative canonical resource references,
 * and synthesizes evidence-grounded responses.
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
                You are SecretVault AI Copilot — an intelligent DevSecOps assistant that operates dynamically in 3 modes:
                1. CHATBOT MODE: Natural conversations ("Hi", "What can you help me with?", "Explain workspaces/rotation/encryption", "Who am I?").
                2. SECURITY / SYSTEM ANALYST MODE: Live telemetry investigations ("What is wrong with my workspace?", "Why did sync fail?", "What are my biggest risks?", "Which credentials are overdue?").
                3. AGENT MODE: Proposing safe remediation actions and step-up approvals ("Rotate credential", "Reconcile sync drift", "Prepare a fix").

                ACTIVE CONTEXT:
                - Active Workspace ID: %s
                - Active User ID: %s
                - Active User Role: %s

                CORE RULES:
                1. ZERO PLAINTEXT INVARIANT: NEVER output, request, or simulate raw plaintext secrets, passwords, tokens, or private keys. Work strictly with safe metadata, fingerprints, and policy states.
                2. DYNAMIC REAL-DATA QUERIES: Never hallucinate or invent user state, workspaces, projects, or invitations.
                   - For user state or invitations: Call `user.getCurrent` or `user.invitations`.
                   - For projects / environments: Call `project.list` or `environment.list`.
                   - For security findings / posture: Call `security.findings` or `security.posture`.
                   - For rotation / sync / audit: Call `rotation.status`, `sync.status`, `audit.search`.
                3. NEVER TRUST MODEL-GENERATED EXTERNAL URLS: Mention canonical resources by name/id; internal routing is managed server-side.
                4. CONCISE & FACTUAL: Provide structured bullet points, clear recommendations, and evidence-grounded facts.
                """.formatted(
                        workspaceId != null ? workspaceId.toString() : "None",
                        userId != null ? userId.toString() : "Anonymous",
                        userRole != null ? userRole : "DEVELOPER"
                ) + knowledgePrompt;

        // 4. Assemble Messages
        List<ChatMessage> messages = new ArrayList<>();
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            messages.addAll(conversationHistory);
        }
        messages.add(ChatMessage.user(sanitizedPrompt));

        List<AiToolDefinition> toolDefinitions = toolRegistry.getToolDefinitions();
        Set<String> executedToolSignatures = new HashSet<>();
        List<TelemetryEvidence> collectedEvidence = new ArrayList<>();
        List<AiResourceReference> collectedResources = new ArrayList<>();
        List<AiActionProposal> collectedActions = new ArrayList<>();
        int totalToolCallsCount = 0;
        String finalAnswer = null;
        double confidence = 0.95;
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

                // Process tool results for evidence and canonical resources
                if (result.success()) {
                    collectedEvidence.add(new TelemetryEvidence(
                            "EV_TOOL_" + tc.name().toUpperCase().replace(".", "_"),
                            "Tool Output: " + tc.name(),
                            "Server-verified metadata from " + tc.name(),
                            workspaceId != null ? workspaceId.toString() : "system",
                            Instant.now()
                    ));

                    extractResourcesAndActionsFromToolResult(tc.name(), result.outputJson(), workspaceId, collectedResources, collectedActions);
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
                    workspaceId != null ? workspaceId.toString() : "system",
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
                collectedResources,
                recommendations,
                collectedActions,
                false,
                List.of(),
                List.of("Zero-knowledge metadata isolation enforced", "Server-side RBAC boundary applied"),
                "AI responses are strictly advisory and require authorized approval before executing changes.",
                Instant.now()
        );
    }

    private void extractResourcesAndActionsFromToolResult(
            String toolName,
            String outputJson,
            UUID workspaceId,
            List<AiResourceReference> resources,
            List<AiActionProposal> actions
    ) {
        if (outputJson == null || outputJson.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(outputJson);

            // Handle user.invitations or invitation.list
            if (toolName.contains("invitation") && root.has("invitations") && root.get("invitations").isArray()) {
                for (JsonNode inv : root.get("invitations")) {
                    String invId = inv.has("invitationId") ? inv.get("invitationId").asText() : "";
                    String wsName = inv.has("workspaceName") ? inv.get("workspaceName").asText() : "Workspace";
                    String role = inv.has("role") ? inv.get("role").asText() : "MEMBER";
                    String inviter = inv.has("invitedByName") ? inv.get("invitedByName").asText() : "Admin";
                    String status = inv.has("status") ? inv.get("status").asText() : "PENDING";
                    String expiresAt = inv.has("expiresAt") ? inv.get("expiresAt").asText() : "";

                    if (!invId.isBlank()) {
                        UUID parsedInvId = UUID.fromString(invId);
                        resources.add(AiResourceReference.invitation(parsedInvId, workspaceId, wsName, role, inviter, status, expiresAt));
                        actions.add(AiActionProposal.openInvitation(invId));
                    }
                }
            }

            // Handle project.list
            if (toolName.contains("project") && root.has("projects") && root.get("projects").isArray()) {
                for (JsonNode p : root.get("projects")) {
                    String pId = p.has("id") ? p.get("id").asText() : "";
                    String pName = p.has("name") ? p.get("name").asText() : "Project";
                    String slug = p.has("slug") ? p.get("slug").asText() : "";
                    int envCount = p.has("environmentCount") ? p.get("environmentCount").asInt() : 0;
                    int secCount = p.has("secretCount") ? p.get("secretCount").asInt() : 0;

                    if (!pId.isBlank()) {
                        resources.add(AiResourceReference.project(UUID.fromString(pId), workspaceId, pName, slug, envCount, secCount));
                    }
                }
            }

            // Handle security.findings
            if (toolName.contains("finding") || toolName.contains("security")) {
                if (root.has("findings") && root.get("findings").isArray()) {
                    for (JsonNode f : root.get("findings")) {
                        String fId = f.has("id") ? f.get("id").asText() : "";
                        String title = f.has("title") ? f.get("title").asText() : "Security Finding";
                        String severity = f.has("severity") ? f.get("severity").asText() : "HIGH";
                        String category = f.has("category") ? f.get("category").asText() : "SECURITY";
                        String status = f.has("status") ? f.get("status").asText() : "OPEN";

                        if (!fId.isBlank()) {
                            resources.add(AiResourceReference.finding(UUID.fromString(fId), workspaceId, title, severity, category, status));
                        }
                    }
                }
            }

            // Handle rotation.status
            if (toolName.contains("rotation") && root.has("rotations") && root.get("rotations").isArray()) {
                for (JsonNode r : root.get("rotations")) {
                    String rId = r.has("id") ? r.get("id").asText() : "";
                    String secretKey = r.has("secretKey") ? r.get("secretKey").asText() : "Credential";
                    String status = r.has("status") ? r.get("status").asText() : "SCHEDULED";
                    String nextDue = r.has("nextDue") ? r.get("nextDue").asText() : "";

                    if (!rId.isBlank()) {
                        resources.add(AiResourceReference.rotation(UUID.fromString(rId), workspaceId, secretKey, status, nextDue));
                        if ("OVERDUE".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
                            actions.add(AiActionProposal.rotateSecret(rId, secretKey, "PROD", 4));
                        }
                    }
                }
            }

            // Handle sync.status
            if (toolName.contains("sync") && root.has("syncJobs") && root.get("syncJobs").isArray()) {
                for (JsonNode s : root.get("syncJobs")) {
                    String sId = s.has("id") ? s.get("id").asText() : "";
                    String providerType = s.has("providerType") ? s.get("providerType").asText() : "Cloud";
                    String status = s.has("status") ? s.get("status").asText() : "IN_SYNC";
                    String lastSyncAt = s.has("lastSyncAt") ? s.get("lastSyncAt").asText() : "";

                    if (!sId.isBlank()) {
                        resources.add(AiResourceReference.syncJob(UUID.fromString(sId), workspaceId, providerType, status, lastSyncAt));
                        if ("DRIFT_DETECTED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
                            actions.add(AiActionProposal.reconcileSync(sId, providerType, "PROD"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Resource extraction from tool result skipped: {}", e.getMessage());
        }
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
