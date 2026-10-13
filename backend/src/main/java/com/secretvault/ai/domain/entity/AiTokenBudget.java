package com.secretvault.ai.domain.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_token_budgets")
public class AiTokenBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false, unique = true)
    private UUID workspaceId;

    @Column(name = "monthly_token_budget", nullable = false)
    private int monthlyTokenBudget = 1000000;

    @Column(name = "tokens_consumed_this_month", nullable = false)
    private int tokensConsumedThisMonth = 0;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute = 60;

    @Column(name = "inquiries_this_minute", nullable = false)
    private int inquiriesThisMinute = 0;

    @Column(name = "minute_window_start", nullable = false)
    private Instant minuteWindowStart = Instant.now();

    @Column(name = "month_window_start", nullable = false)
    private Instant monthWindowStart = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AiTokenBudget() {
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

    public int getMonthlyTokenBudget() {
        return monthlyTokenBudget;
    }

    public void setMonthlyTokenBudget(int monthlyTokenBudget) {
        this.monthlyTokenBudget = monthlyTokenBudget;
    }

    public int getTokensConsumedThisMonth() {
        return tokensConsumedThisMonth;
    }

    public void setTokensConsumedThisMonth(int tokensConsumedThisMonth) {
        this.tokensConsumedThisMonth = tokensConsumedThisMonth;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getInquiriesThisMinute() {
        return inquiriesThisMinute;
    }

    public void setInquiriesThisMinute(int inquiriesThisMinute) {
        this.inquiriesThisMinute = inquiriesThisMinute;
    }

    public Instant getMinuteWindowStart() {
        return minuteWindowStart;
    }

    public void setMinuteWindowStart(Instant minuteWindowStart) {
        this.minuteWindowStart = minuteWindowStart;
    }

    public Instant getMonthWindowStart() {
        return monthWindowStart;
    }

    public void setMonthWindowStart(Instant monthWindowStart) {
        this.monthWindowStart = monthWindowStart;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
