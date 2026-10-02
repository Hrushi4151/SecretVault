package com.secretvault.sync.model;

/**
 * Policy guiding how drift discrepancies are evaluated and reconciled with external providers.
 */
public enum ReconciliationPolicy {
    /**
     * Compare state and register drift findings without mutating remote provider.
     */
    DETECT_ONLY,

    /**
     * Push SecretVault desired state to external provider (upsert existing & create missing).
     * Conservative: does not delete unexpected remote secrets unless explicitly permitted.
     */
    PUSH_SECRETVAULT_TO_PROVIDER,

    /**
     * Complete safe reconciliation: creates missing and updates mismatched secrets.
     */
    SAFE_RECONCILIATION
}
