package com.secretvault.security.risk.model;

/**
 * Standardized risk severity levels according to SecretVault deterministic scoring.
 * 0–19: LOW
 * 20–39: MODERATE
 * 40–59: ELEVATED
 * 60–79: HIGH
 * 80–100: CRITICAL
 */
public enum RiskLevel {
    LOW(0, 19),
    MODERATE(20, 39),
    ELEVATED(40, 59),
    HIGH(60, 79),
    CRITICAL(80, 100);

    private final int minScore;
    private final int maxScore;

    RiskLevel(int minScore, int maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public static RiskLevel fromScore(int score) {
        int bounded = Math.max(0, Math.min(100, score));
        if (bounded >= 80) return CRITICAL;
        if (bounded >= 60) return HIGH;
        if (bounded >= 40) return ELEVATED;
        if (bounded >= 20) return MODERATE;
        return LOW;
    }
}
