package com.secretvault.security.risk.model;

import java.util.List;

/**
 * Explainable factor contributing to the deterministic workspace risk score.
 */
public record RiskFactorExplanation(
        String factorKey,
        String name,
        int weight,
        String reason,
        List<String> evidenceReferences
) {}
