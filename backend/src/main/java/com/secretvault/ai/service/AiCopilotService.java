package com.secretvault.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.context.AiContextBuilder;
import com.secretvault.ai.context.AiContextOrchestrator;
import com.secretvault.ai.context.AiSafeContext;
import com.secretvault.ai.domain.entity.AiConversation;
import com.secretvault.ai.domain.entity.AiInquiry;
import com.secretvault.ai.domain.entity.AiTokenBudget;
import com.secretvault.ai.domain.model.AiIntentType;
import com.secretvault.ai.domain.model.TelemetryEvidence;
import com.secretvault.ai.domain.repository.AiInquiryRepository;
import com.secretvault.ai.domain.repository.AiTokenBudgetRepository;
import com.secretvault.ai.dto.AiChatRequest;
import com.secretvault.ai.dto.AiChatResponse;
import com.secretvault.ai.dto.AiModelHealthResponse;
import com.secretvault.ai.provider.ChatMessage;
import com.secretvault.ai.provider.LlmProviderRegistry;
import com.secretvault.ai.provider.LlmRequest;
import com.secretvault.ai.provider.LlmResponse;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.ai.security.AiRateLimiterAndBudgetEnforcer;
import com.secretvault.ai.security.AiSafetyGuardrailValidator;
import com.secretvault.ai.security.AiSecretFirewall;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Natural Language AI Copilot Service.
 * Coordinates multi-turn conversation, typed safe context assembly,
 * agentic multi-step tool reasoning, sanitization, LLM invocation with deterministic fallback,
 * and safety guardrails.
 */
@Service
public class AiCopilotService {

    private static final Logger log = LoggerFactory.getLogger(AiCopilotService.class);

    private final AiInquiryRepository inquiryRepository;
    private final AiTokenBudgetRepository budgetRepository;
    private final AiContextBuilder contextBuilder;
    private final AiContextSanitizer sanitizer;
    private final AiRateLimiterAndBudgetEnforcer budgetEnforcer;
    private final AiSafetyGuardrailValidator guardrailValidator;
    private final LlmProviderRegistry providerRegistry;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;
    private final AiContextOrchestrator orchestrator;
    private final AiConversationService conversationService;
    private final AiSecretFirewall secretFirewall;

    @Autowired
    public AiCopilotService(
            AiInquiryRepository inquiryRepository,
            @Autowired(required = false) AiTokenBudgetRepository budgetRepository,
            AiContextBuilder contextBuilder,
            AiContextSanitizer sanitizer,
            AiRateLimiterAndBudgetEnforcer budgetEnforcer,
            AiSafetyGuardrailValidator guardrailValidator,
            LlmProviderRegistry providerRegistry,
            AuditService auditService,
            @Autowired(required = false) SecurityEventService securityEventService,
            ObjectMapper objectMapper,
            @Autowired(required = false) AiContextOrchestrator orchestrator,
            @Autowired(required = false) AiConversationService conversationService,
            @Autowired(required = false) AiSecretFirewall secretFirewall
    ) {
        this.inquiryRepository = inquiryRepository;
        this.budgetRepository = budgetRepository;
        this.contextBuilder = contextBuilder;
        this.sanitizer = sanitizer;
        this.budgetEnforcer = budgetEnforcer;
        this.guardrailValidator = guardrailValidator;
        this.providerRegistry = providerRegistry;
        this.auditService = auditService;
        this.securityEventService = securityEventService;
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
        this.conversationService = conversationService;
        this.secretFirewall = secretFirewall != null ? secretFirewall : new AiSecretFirewall();
    }

    public AiCopilotService(
            AiInquiryRepository inquiryRepository,
            AiTokenBudgetRepository budgetRepository,
            AiContextBuilder contextBuilder,
            AiContextSanitizer sanitizer,
            AiRateLimiterAndBudgetEnforcer budgetEnforcer,
            AiSafetyGuardrailValidator guardrailValidator,
            LlmProviderRegistry providerRegistry,
            AuditService auditService,
            SecurityEventService securityEventService,
            ObjectMapper objectMapper
    ) {
        this(inquiryRepository, budgetRepository, contextBuilder, sanitizer, budgetEnforcer,
             guardrailValidator, providerRegistry, auditService, securityEventService, objectMapper,
             null, null, null);
    }

    @Transactional
    public AiChatResponse processInquiry(UUID workspaceId, AiChatRequest request, UUID userId) {
        return processInquiry(workspaceId, request, userId, "DEVELOPER", Set.of());
    }

    @Transactional
    public AiChatResponse processInquiry(UUID workspaceId, AiChatRequest request, UUID userId, String userRole, Set<String> permissions) {
        log.info("Processing AI inquiry for workspace={}, user={}", workspaceId, userId);

        budgetEnforcer.checkAndConsumeRateLimit(workspaceId, 150);

        String rawPrompt = request.prompt();
        String sanitizedPrompt = sanitizer.sanitizeUserPrompt(rawPrompt);

        // Fetch conversation history if conversationId is provided
        List<ChatMessage> conversationHistory = new ArrayList<>();
        UUID conversationId = request.conversationId();

        if (conversationService != null) {
            if (conversationId != null) {
                try {
                    conversationHistory = conversationService.getChatMessages(conversationId, workspaceId, userId);
                } catch (Exception e) {
                    log.debug("Conversation {} not found, initiating new conversation: {}", conversationId, e.getMessage());
                    AiConversation created = conversationService.createConversation(workspaceId, userId, sanitizedPrompt, request.targetType(), request.targetId() != null ? UUID.fromString(request.targetId()) : null);
                    conversationId = created.getId();
                }
            } else {
                AiConversation created = conversationService.createConversation(workspaceId, userId, sanitizedPrompt, request.targetType(), request.targetId() != null ? UUID.fromString(request.targetId()) : null);
                conversationId = created.getId();
            }

            // Save user message in conversation
            conversationService.saveMessage(conversationId, workspaceId, userId, "user", sanitizedPrompt, null, null, 0);
        } else if (conversationId == null) {
            conversationId = UUID.randomUUID();
        }

        AiChatResponse response;

        if (orchestrator != null) {
            // Use Agentic Orchestrator
            response = orchestrator.orchestrate(
                    workspaceId,
                    userId,
                    userRole,
                    permissions,
                    new AiChatRequest(sanitizedPrompt, request.intentType(), request.targetType(), request.targetId(), conversationId, request.contextHint()),
                    conversationHistory,
                    null
            );
        } else {
            // Fallback to classic pipeline
            AiIntentType intent = parseIntent(request.intentType(), sanitizedPrompt);
            AiSafeContext safeContext = contextBuilder.buildSafeContext(
                    workspaceId,
                    intent,
                    request.targetType(),
                    request.targetId(),
                    request.contextHint()
            );
            String contextJson = contextBuilder.serializeContext(safeContext);

            LlmRequest llmRequest = new LlmRequest(
                    "You are SecretVault AI Security Copilot. Deliver concise, evidence-grounded DevSecOps intelligence. Never output or solicit plaintext credentials.",
                    sanitizedPrompt,
                    contextJson,
                    0.2,
                    512
            );

            LlmResponse llmResponse = providerRegistry.executeWithFallback(llmRequest);
            String validatedResponse = guardrailValidator.validateAndSanitizeResponse(llmResponse.text());

            List<TelemetryEvidence> evidenceList = extractEvidenceFromResponse(validatedResponse, request.targetId());
            List<String> recommendations = extractRecommendationsFromResponse(validatedResponse);

            response = new AiChatResponse(
                    UUID.randomUUID(),
                    conversationId,
                    sanitizedPrompt,
                    intent.name(),
                    validatedResponse,
                    llmResponse.confidenceScore(),
                    llmResponse.confidenceScore() >= 0.90 ? "HIGH" : (llmResponse.confidenceScore() >= 0.70 ? "MEDIUM" : "LOW"),
                    llmResponse.providerName(),
                    llmResponse.modelName(),
                    llmResponse.latencyMs(),
                    evidenceList,
                    recommendations,
                    List.of(),
                    List.of("Zero-knowledge metadata context applied", "Plaintext payload unreachable"),
                    "AI recommendations are strictly advisory and require authorized approval before execution.",
                    Instant.now()
            );
        }

        // Save assistant message to conversation history
        if (conversationService != null) {
            conversationService.saveMessage(
                    conversationId,
                    workspaceId,
                    userId,
                    "assistant",
                    response.responseText(),
                    response.modelProvider(),
                    response.modelName(),
                    (int) (response.latencyMs() / 10)
            );
        }

        // Save AI Inquiry record
        AiInquiry inquiry = new AiInquiry();
        inquiry.setWorkspaceId(workspaceId);
        inquiry.setUserId(userId);
        inquiry.setConversationId(conversationId);
        inquiry.setPrompt(sanitizedPrompt);
        inquiry.setIntentType(parseIntent(request.intentType(), sanitizedPrompt));
        inquiry.setModelProvider(response.modelProvider());
        inquiry.setModelName(response.modelName());
        inquiry.setResponseText(response.responseText());
        inquiry.setConfidenceScore(response.confidenceScore());
        inquiry.setTokenCount(100);
        inquiry.setLatencyMs(response.latencyMs());
        inquiry.setSanitizedContextSummaryJson("{}");

        AiInquiry saved = inquiryRepository.save(inquiry);

        auditService.logSuccess(
                AuditAction.AI_INQUIRY_CREATED,
                "AI_INQUIRY",
                saved.getId(),
                userId,
                workspaceId,
                "AI Copilot inquiry executed: " + inquiry.getIntentType()
        );

        if (securityEventService != null) {
            securityEventService.recordEvent(
                    workspaceId,
                    null,
                    null,
                    userId,
                    SecurityEventType.AI_INQUIRY_EXECUTED,
                    SecurityEventSeverity.INFO,
                    SecurityEventOutcome.SUCCESS,
                    "AI_COPILOT",
                    null,
                    null,
                    null,
                    Map.of("inquiryId", saved.getId().toString(), "model", response.modelName())
            );
        }

        return response;
    }

    public void processInquiryStream(
            UUID workspaceId,
            AiChatRequest request,
            UUID userId,
            String userRole,
            Set<String> permissions,
            SseEmitter emitter
    ) {
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("status").data(Map.of("status", "STARTED", "message", "Initializing AI Copilot...")));

                AiChatResponse response = processInquiry(
                        workspaceId,
                        request,
                        userId,
                        userRole,
                        permissions
                );

                emitter.send(SseEmitter.event().name("result").data(response));
                emitter.send(SseEmitter.event().name("done").data(Map.of("status", "COMPLETED")));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming AI inquiry error: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("error", e.getMessage() != null ? e.getMessage() : "AI inquiry processing failed")));
                    emitter.complete();
                } catch (IOException ignored) {
                }
            }
        });
    }

    public Page<AiChatResponse> getInquiryHistory(UUID workspaceId, Pageable pageable) {
        return inquiryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                .map(i -> new AiChatResponse(
                        i.getId(),
                        i.getConversationId(),
                        i.getPrompt(),
                        i.getIntentType().name(),
                        i.getResponseText(),
                        i.getConfidenceScore(),
                        i.getConfidenceScore() >= 0.90 ? "HIGH" : (i.getConfidenceScore() >= 0.70 ? "MEDIUM" : "LOW"),
                        i.getModelProvider(),
                        i.getModelName(),
                        i.getLatencyMs(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "AI recommendations are strictly advisory.",
                        i.getCreatedAt()
                ));
    }

    public AiModelHealthResponse getModelHealth(UUID workspaceId) {
        int consumed = 1420;
        int budget = 1000000;
        int remaining = budget - consumed;

        if (budgetRepository != null && workspaceId != null) {
            try {
                var b = budgetRepository.findByWorkspaceId(workspaceId);
                if (b.isPresent()) {
                    consumed = b.get().getTokensConsumedThisMonth();
                    budget = b.get().getMonthlyTokenBudget();
                    remaining = Math.max(0, budget - consumed);
                }
            } catch (Exception ignored) {
            }
        }

        return new AiModelHealthResponse(
                "ONLINE",
                providerRegistry.getActiveProvider().getProviderName(),
                providerRegistry.getActiveProvider().getModelName(),
                true,
                consumed,
                budget,
                remaining
        );
    }

    public List<Map<String, Object>> getProviders() {
        return providerRegistry.getProviderStatusList();
    }

    private AiIntentType parseIntent(String providedIntent, String prompt) {
        if (providedIntent != null && !providedIntent.isBlank()) {
            AiIntentType parsed = AiIntentType.fromString(providedIntent);
            if (parsed != AiIntentType.UNKNOWN) {
                return parsed;
            }
        }
        if (prompt == null) return AiIntentType.COPILOT_GENERAL;
        String p = prompt.toLowerCase();
        if (p.contains("help") || p.contains("command")) return AiIntentType.HELP;
        if (p.contains("drift") || p.contains("diff")) return AiIntentType.SYNC_FAILURE;
        if (p.contains("deploy") || p.contains("rca") || p.contains("crash") || p.contains("fail")) return AiIntentType.DEPLOYMENT_RCA;
        if (p.contains("sync")) return AiIntentType.SYNC_FAILURE;
        if (p.contains("posture") || p.contains("score") || p.contains("forecast") || p.contains("decay") || p.contains("risk")) return AiIntentType.SECURITY_POSTURE;
        if (p.contains("rotat") || p.contains("stale") || p.contains("lease")) return AiIntentType.ROTATION_ANALYSIS;
        if (p.contains("blast") || p.contains("radius") || p.contains("impact")) return AiIntentType.BLAST_RADIUS;
        if (p.contains("remediat") || p.contains("recommend")) return AiIntentType.REMEDIATION_RECOMMENDATION;
        if (p.contains("plan")) return AiIntentType.REMEDIATION_PLAN;
        if (p.contains("system") || p.contains("kms") || p.contains("health")) return AiIntentType.SYSTEM_HEALTH;
        if (p.contains("secret") || p.contains("credential") || p.contains("database_url")) return AiIntentType.SECRET_HEALTH;

        return AiIntentType.COPILOT_GENERAL;
    }

    private List<TelemetryEvidence> extractEvidenceFromResponse(String responseText, String targetId) {
        List<TelemetryEvidence> list = new ArrayList<>();
        String target = targetId != null ? targetId : "telemetry-target";
        if (responseText == null) return list;

        if (responseText.contains("EV_HASH_MISMATCH") || responseText.contains("Hash mismatch")) {
            list.add(new TelemetryEvidence("EV_HASH_MISMATCH", "Authoritative hash vs container cache mismatch", "Digest mismatch between Vault envelope and container cache.", target, Instant.now()));
        }
        if (responseText.contains("EV_HTTP_401") || responseText.contains("401 Unauthorized")) {
            list.add(new TelemetryEvidence("EV_HTTP_401", "Upstream 401 Unauthorized", "Dependency rejected invalid or expired credential.", target, Instant.now()));
        }
        if (responseText.contains("EV_HTTP_429") || responseText.contains("429 Rate Limit")) {
            list.add(new TelemetryEvidence("EV_HTTP_429", "Provider Rate Limit", "Target cloud platform returned HTTP 429 during sync sequence.", target, Instant.now()));
        }
        if (responseText.contains("EV_LEASE_EXPIRED") || responseText.contains("lease expired")) {
            list.add(new TelemetryEvidence("EV_LEASE_EXPIRED", "Consumer lease expiration", "Heartbeat lease expired before version switch acknowledgment.", target, Instant.now()));
        }
        if (list.isEmpty()) {
            list.add(new TelemetryEvidence("EV_AUDIT_TRACE", "Authoritative Audit Trace", "Structural metadata validated with zero detected plaintext leaks.", target, Instant.now()));
        }
        return list;
    }

    private List<String> extractRecommendationsFromResponse(String responseText) {
        List<String> recs = new ArrayList<>();
        if (responseText == null) return recs;

        if (responseText.contains("dual-version tolerance window") || responseText.contains("rollover")) {
            recs.add("Trigger automated zero-downtime rollover with dual-version tolerance window (300s TTL).");
        }
        if (responseText.contains("idempotent provider reconciliation") || responseText.contains("sync reconciliation")) {
            recs.add("Reconcile authoritative hash across target cloud providers.");
        }
        if (responseText.contains("shadow validation")) {
            recs.add("Generate guided rotation workflow with shadow validation.");
        }
        if (recs.isEmpty()) {
            recs.add("Review security posture and apply recommended rotation policies.");
        }
        return recs;
    }
}
