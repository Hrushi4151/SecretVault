package com.secretvault.security.finding.model;

import java.util.Set;

/**
 * Finding lifecycle statuses with explicit transition verification.
 */
public enum FindingStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    FALSE_POSITIVE;

    public boolean canTransitionTo(FindingStatus next) {
        if (next == null || this == next) {
            return true;
        }

        return switch (this) {
            case OPEN -> Set.of(ACKNOWLEDGED, IN_PROGRESS, RESOLVED, FALSE_POSITIVE).contains(next);
            case ACKNOWLEDGED -> Set.of(OPEN, IN_PROGRESS, RESOLVED, FALSE_POSITIVE).contains(next);
            case IN_PROGRESS -> Set.of(OPEN, ACKNOWLEDGED, RESOLVED, FALSE_POSITIVE).contains(next);
            case RESOLVED -> Set.of(OPEN).contains(next); // Re-open on regression
            case FALSE_POSITIVE -> Set.of(OPEN).contains(next); // Re-open on reassessment
        };
    }

    public boolean isUnresolved() {
        return this == OPEN || this == ACKNOWLEDGED || this == IN_PROGRESS;
    }
}
