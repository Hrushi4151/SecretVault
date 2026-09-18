package com.secretvault.secret.entity;

/**
 * Categorizes the operation and lineage type of an immutable SecretVersion.
 */
public enum VersionType {
    INITIAL,
    VALUE_UPDATE,
    ROLLBACK,
    PROMOTION,
    BRANCH_COMMIT,
    MERGE,
    IMPORT,
    ROTATION
}
