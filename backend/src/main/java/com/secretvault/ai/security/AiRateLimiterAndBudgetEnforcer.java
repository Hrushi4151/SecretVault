package com.secretvault.ai.security;

import com.secretvault.ai.domain.entity.AiTokenBudget;
import com.secretvault.ai.domain.repository.AiTokenBudgetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Enforces per-minute rate limits and monthly token budgets per workspace.
 */
@Component
public class AiRateLimiterAndBudgetEnforcer {

    private final AiTokenBudgetRepository budgetRepository;

    public AiRateLimiterAndBudgetEnforcer(AiTokenBudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Transactional
    public void checkAndConsumeRateLimit(UUID workspaceId, int estimatedTokens) {
        AiTokenBudget budget = budgetRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    AiTokenBudget b = new AiTokenBudget();
                    b.setWorkspaceId(workspaceId);
                    return budgetRepository.save(b);
                });

        Instant now = Instant.now();

        // 1. Check & reset minute window
        if (Duration.between(budget.getMinuteWindowStart(), now).toSeconds() >= 60) {
            budget.setMinuteWindowStart(now);
            budget.setInquiriesThisMinute(0);
        }

        if (budget.getInquiriesThisMinute() >= budget.getRateLimitPerMinute()) {
            throw new IllegalStateException("AI Rate Limit Exceeded: Maximum " + budget.getRateLimitPerMinute()
                    + " queries per minute permitted for workspace.");
        }

        // 2. Check & reset monthly window
        if (Duration.between(budget.getMonthWindowStart(), now).toDays() >= 30) {
            budget.setMonthWindowStart(now);
            budget.setTokensConsumedThisMonth(0);
        }

        if (budget.getTokensConsumedThisMonth() + estimatedTokens > budget.getMonthlyTokenBudget()) {
            throw new IllegalStateException("AI Token Budget Exceeded: Workspace monthly budget of "
                    + budget.getMonthlyTokenBudget() + " tokens exhausted.");
        }

        budget.setInquiriesThisMinute(budget.getInquiriesThisMinute() + 1);
        budget.setTokensConsumedThisMonth(budget.getTokensConsumedThisMonth() + estimatedTokens);
        budget.setUpdatedAt(now);
        budgetRepository.save(budget);
    }
}
