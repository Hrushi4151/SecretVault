package com.secretvault.environment.access.entity;

/**
 * Fine-grained permission levels for scoped environment access.
 */
public enum PermissionLevel {
    READ,
    WRITE,
    MANAGE;

    public boolean canRead() {
        return true;
    }

    public boolean canWrite() {
        return this == WRITE || this == MANAGE;
    }

    public boolean canManage() {
        return this == MANAGE;
    }
}
