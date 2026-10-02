package com.secretvault.oidc.adapter;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Claim adapter for GitLab CI/CD OIDC tokens.
 * Extracts standard GitLab claims including project_path, ref, environment, user_login, etc.
 */
@Component
public class GitLabCiClaimAdapter implements ClaimAdapter {

    @Override
    public Map<String, Object> adaptClaims(Map<String, Object> rawClaims) {
        if (rawClaims == null) {
            return Map.of();
        }

        Map<String, Object> adapted = new HashMap<>(rawClaims);

        normalizeClaim(adapted, "project_path", "project_path");
        normalizeClaim(adapted, "project_id", "project_id");
        normalizeClaim(adapted, "namespace_id", "namespace_id");
        normalizeClaim(adapted, "ref", "ref");
        normalizeClaim(adapted, "ref_type", "ref_type");
        normalizeClaim(adapted, "pipeline_id", "pipeline_id");
        normalizeClaim(adapted, "pipeline_source", "pipeline_source");
        normalizeClaim(adapted, "environment", "environment");
        normalizeClaim(adapted, "environment_action", "environment_action");
        normalizeClaim(adapted, "user_login", "user_login");
        normalizeClaim(adapted, "sub", "sub");
        normalizeClaim(adapted, "iss", "iss");
        normalizeClaim(adapted, "aud", "aud");

        return adapted;
    }

    private void normalizeClaim(Map<String, Object> map, String targetKey, String sourceKey) {
        if (!map.containsKey(targetKey) && map.containsKey(sourceKey)) {
            map.put(targetKey, map.get(sourceKey));
        }
    }
}
