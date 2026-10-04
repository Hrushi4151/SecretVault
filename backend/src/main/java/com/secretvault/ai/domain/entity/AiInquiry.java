package com.secretvault.ai.domain.entity;

import com.secretvault.ai.domain.model.AiIntentType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_inquiries")
public class AiInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent_type", nullable = false, length = 64)
    private AiIntentType intentType = AiIntentType.GENERAL_QUERY;

    @Column(name = "model_provider", nullable = false, length = 64)
    private String modelProvider;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore = 0.95;

    @Column(name = "token_count", nullable = false)
    private int tokenCount = 0;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs = 0;

    @Column(name = "sanitized_context_summary_json", columnDefinition = "TEXT")
    private String sanitizedContextSummaryJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AiInquiry() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public AiIntentType getIntentType() {
        return intentType;
    }

    public void setIntentType(AiIntentType intentType) {
        this.intentType = intentType;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getSanitizedContextSummaryJson() {
        return sanitizedContextSummaryJson;
    }

    public void setSanitizedContextSummaryJson(String sanitizedContextSummaryJson) {
        this.sanitizedContextSummaryJson = sanitizedContextSummaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
