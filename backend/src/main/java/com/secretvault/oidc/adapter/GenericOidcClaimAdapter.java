package com.secretvault.oidc.adapter;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Claim adapter for generic OpenID Connect providers.
 */
@Component
public class GenericOidcClaimAdapter implements ClaimAdapter {

    @Override
    public Map<String, Object> adaptClaims(Map<String, Object> rawClaims) {
        if (rawClaims == null) {
            return Map.of();
        }
        return new HashMap<>(rawClaims);
    }
}
