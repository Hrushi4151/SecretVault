package com.secretvault.oidc.adapter;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Claim adapter for GitHub Actions OIDC tokens.
 * Extracts standard GitHub claims including repository, ref, workflow, environment, actor, and sha.
 */
@Component
public class GitHubActionsClaimAdapter implements ClaimAdapter {

    @Override
    public Map<String, Object> adaptClaims(Map<String, Object> rawClaims) {
        if (rawClaims == null) {
            return Map.of();
        }

        Map<String, Object> adapted = new HashMap<>(rawClaims);

        // Ensure canonical keys are available
        normalizeClaim(adapted, "repository", "repository");
        normalizeClaim(adapted, "repository_owner", "repository_owner");
        normalizeClaim(adapted, "repository_id", "repository_id");
        normalizeClaim(adapted, "ref", "ref");
        normalizeClaim(adapted, "ref_type", "ref_type");
        normalizeClaim(adapted, "workflow", "workflow");
        normalizeClaim(adapted, "environment", "environment");
        normalizeClaim(adapted, "actor", "actor");
        normalizeClaim(adapted, "actor_id", "actor_id");
        normalizeClaim(adapted, "event_name", "event_name");
        normalizeClaim(adapted, "sha", "sha");
        normalizeClaim(adapted, "sub", "sub");
        normalizeClaim(adapted, "iss", "iss");
        normalizeClaim(adapted, "aud", "aud");

        // Parse branch name helper if ref starts with refs/heads/
        Object refObj = adapted.get("ref");
        if (refObj instanceof String refStr) {
            if (refStr.startsWith("refs/heads/")) {
                adapted.put("branch", refStr.substring("refs/heads/".length()));
            } else if (refStr.startsWith("refs/tags/")) {
                adapted.put("tag", refStr.substring("refs/tags/".length()));
            }
        }

        return adapted;
    }

    private void normalizeClaim(Map<String, Object> map, String targetKey, String sourceKey) {
        if (!map.containsKey(targetKey) && map.containsKey(sourceKey)) {
            map.put(targetKey, map.get(sourceKey));
        }
    }
}
