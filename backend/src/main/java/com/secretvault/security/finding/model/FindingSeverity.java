package com.secretvault.security.finding.model;

public enum FindingSeverity {
    LOW(10),
    MEDIUM(25),
    HIGH(50),
    CRITICAL(80);

    private final int defaultRiskWeight;

    FindingSeverity(int defaultRiskWeight) {
        this.defaultRiskWeight = defaultRiskWeight;
    }

    public int getDefaultRiskWeight() {
        return defaultRiskWeight;
    }
}
