package com.secretvault.rotation.model;

/**
 * Supported secret payload categories for automated generation and rotation.
 */
public enum SecretType {
    RANDOM_STRING,
    RANDOM_BYTES,
    API_KEY,
    PASSWORD,
    TOKEN,
    SSH_KEY,
    CERTIFICATE,
    DATABASE_CREDENTIAL,
    PROVIDER_CREDENTIAL,
    CUSTOM
}
