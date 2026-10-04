package com.secretvault.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.entity.AiInquiry;
import com.secretvault.ai.domain.model.AiIntentType;
import com.secretvault.ai.domain.repository.AiInquiryRepository;
import com.secretvault.ai.dto.AiChatRequest;
import com.secretvault.ai.dto.AiChatResponse;
import com.secretvault.ai.dto.AiModelHealthResponse;
import com.secretvault.ai.provider.LlmProviderRegistry;
import com.secretvault.ai.provider.LlmRequest;
import com.secretvault.ai.provider.LlmResponse;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.ai.security.AiRateLimiterAndBudgetEnforcer;
import com.secretvault.ai.security.AiSafetyGuardrailValidator;
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

import java.util.Map;
import java.util.UUID;

/**
 * Natural Language AI Copilot Service.
 * Coordinates conversation, intent parsing, sanitization,
 * LLM invocation, and safety guardrails.
 */
@Service
public class AiCopilotService {

    private static final Logger log = LoggerFactory.getLogger(AiCopilotService.class);

    private final AiInquiryRepository inquiryRepository;
    private final AiContextSanitizer sanitizer;
    private final AiRateLimiterAndBudgetEnforcer budgetEnforcer;
    private final AiSafetyGuardrailValidator guardrailValidator;
    private final LlmProviderRegistry providerRegistry;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;

    public AiCopilotService(
            AiInquiryRepository inquiryRepository,
            AiContextSanitizer sanitizer,
            AiRateLimiterAndBudgetEnforcer budgetEnforcer,
            AiSafetyGuardrailValidator guardrailValidator,
            LlmProviderRegistry providerRegistry,
            AuditService auditService,
            @Autowired(required = false) SecurityEventService securityEventService,
            ObjectMapper objectMapper
    ) {
        this.inquiryRepository = inquiryRepository;
        this.sanitizer = sanitizer;
        this.budgetEnforcer = budgetEnforcer;
        this.guardrailValidator = guardrailValidator;
        this.providerRegistry = providerRegistry;
        this.auditService = auditService;
        this.securityEventService = securityEventService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiChatResponse processInquiry(UUID workspaceId, AiChatRequest request, UUID userId) {
        log.info("Processing AI inquiry for workspace={}, user={}", workspaceId, userId);

        budgetEnforcer.checkAndConsumeRateLimit(workspaceId, 150);

        String rawPrompt = request.prompt();
        String sanitizedPrompt = sanitizer.sanitizeUserPrompt(rawPrompt);

        AiIntentType intent = parseIntent(request.intentType(), sanitizedPrompt);

        Map<String, Object> contextMap = Map.of(
                "workspaceId", workspaceId.toString(),
                "intent", intent.name(),
                "zeroKnowledgeEnforced", true
        );
        String contextJson = "{}";
        try {
            contextJson = objectMapper.writeValueAsString(contextMap);
        } catch (Exception ignored) {
        }

        LlmRequest llmRequest = new LlmRequest(
                "You are SecretVault AI Security Copilot. Deliver concise, evidence-grounded DevSecOps intelligence. Never output or solicit plaintext credentials.",
                sanitizedPrompt,
                contextJson,
                0.2,
                512
        );

        LlmResponse llmResponse = providerRegistry.executeWithFallback(llmRequest);
        String validatedResponse = guardrailValidator.validateAndSanitizeResponse(llmResponse.text());

        AiInquiry inquiry = new AiInquiry();
        inquiry.setWorkspaceId(workspaceId);
        inquiry.setUserId(userId);
        inquiry.setPrompt(sanitizedPrompt);
        inquiry.setIntentType(intent);
        inquiry.setModelProvider(llmResponse.providerName());
        inquiry.setModelName(llmResponse.modelName());
        inquiry.setResponseText(validatedResponse);
        inquiry.setConfidenceScore(llmResponse.confidenceScore());
        inquiry.setTokenCount(llmResponse.tokensUsed());
        inquiry.setLatencyMs(llmResponse.latencyMs());
        inquiry.setSanitizedContextSummaryJson(contextJson);

        AiInquiry saved = inquiryRepository.save(inquiry);

        auditService.logSuccess(
                AuditAction.AI_INQUIRY_CREATED,
                "AI_INQUIRY",
                saved.getId(),
                userId,
                workspaceId,
                "AI Copilot inquiry executed: " + intent
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
                    Map.of("inquiryId", saved.getId().toString(), "intent", intent.name())
            );
        }

        return new AiChatResponse(
                saved.getId(),
                saved.getPrompt(),
                saved.getIntentType().name(),
                saved.getResponseText(),
                saved.getConfidenceScore(),
                saved.getModelProvider(),
                saved.getModelName(),
                saved.getLatencyMs(),
                saved.getCreatedAt()
        );
    }

    public Page<AiChatResponse> getInquiryHistory(UUID workspaceId, Pageable pageable) {
        return inquiryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                .map(i -> new AiChatResponse(
                        i.getId(),
                        i.getPrompt(),
                        i.getIntentType().name(),
                        i.getResponseText(),
                        i.getConfidenceScore(),
                        i.getModelProvider(),
                        i.getModelName(),
                        i.getLatencyMs(),
                        i.getCreatedAt()
                ));
    }

    public AiModelHealthResponse getModelHealth(UUID workspaceId) {
        return new AiModelHealthResponse(
                "ONLINE",
                providerRegistry.getActiveProvider().getProviderName(),
                providerRegistry.getActiveProvider().getModelName(),
                true,
                1420,
                1000000,
                998580
        );
    }

    private AiIntentType parseIntent(String providedIntent, String prompt) {
        if (providedIntent != null && !providedIntent.isBlank()) {
            try {
                return AiIntentType.valueOf(providedIntent.trim().toUpperCase());
            } catch (Exception ignored) {
            }
        }
        String p = prompt.toLowerCase();
        if (p.contains("drift")) return AiIntentType.DRIFT_RCA;
        if (p.contains("deploy") || p.contains("rca")) return AiIntentType.DEPLOYMENT_RCA;
        if (p.contains("sync")) return AiIntentType.SYNC_RCA;
        if (p.contains("posture") || p.contains("score")) return AiIntentType.POSTURE_FORECAST;
        if (p.contains("rotat")) return AiIntentType.RECOMMENDATION_TRIAGE;
        if (p.contains("blast") || p.contains("radius")) return AiIntentType.BLAST_RADIUS_INQUIRY;
        return AiIntentType.GENERAL_QUERY;
    }
}
