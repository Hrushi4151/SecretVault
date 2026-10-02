package com.secretvault.sync.model;

/**
 * Standardized drift categories representing discrepancies between
 * SecretVault desired state and external platform provider actual state.
 */
public enum DriftType {
    MISSING_FROM_PROVIDER("Secret exists in SecretVault but is missing in remote provider"),
    EXTRA_IN_PROVIDER("Provider contains an unmanaged secret variable not registered in SecretVault"),
    VALUE_MISMATCH("Secret value or fingerprint differs between SecretVault and provider"),
    NAME_MISMATCH("Secret mapping expects one provider variable name but actual provider name differs"),
    ENVIRONMENT_MISMATCH("Secret is associated with wrong provider environment target"),
    RESOURCE_MAPPING_MISMATCH("Provider resource no longer corresponds to configured mapping"),
    PROVIDER_UNAVAILABLE("Provider API could not be reached or timed out"),
    PERMISSION_DENIED("Provider authentication expired or lack required permissions to inspect secrets"),
    UNSUPPORTED("Provider cannot expose enough information for reliable state comparison");

    private final String description;

    DriftType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
