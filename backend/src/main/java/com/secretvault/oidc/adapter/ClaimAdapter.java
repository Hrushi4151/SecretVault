package com.secretvault.oidc.adapter;

import java.util.Map;

/**
 * Normalizes provider-specific OIDC claim shapes into canonical SecretVault claims.
 */
public interface ClaimAdapter {

    /**
     * Extracts and normalizes provider claims into canonical claim keys.
     */
    Map<String, Object> adaptClaims(Map<String, Object> rawClaims);
}
