package com.secretvault.access.dto;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;

import java.util.UUID;

/**
 * Data transfer object providing transparent "Why do I have access?" lineage.
 * Represents a single permission's effective evaluation state and source attribution.
 */
public record EffectiveAccessExplanation(
        AccessPermission permission,
        String permissionCode,
        String description,
        boolean granted,
        AccessScope scope,
        AccessSourceType sourceType,
        String sourceReference,
        String reason,
        String deniedReason
) {

    public static EffectiveAccessExplanation fromDecision(AccessPermission perm, com.secretvault.access.model.AccessDecision decision) {
        return new EffectiveAccessExplanation(
                perm,
                perm.getCode(),
                perm.getDescription(),
                decision.allowed(),
                decision.scope(),
                decision.sourceType(),
                decision.sourceReference(),
                decision.reason(),
                decision.deniedReason()
        );
    }
}
