package com.secretvault.rotation.model;

/**
 * Strategy for distributing rotated secret versions to consumer workloads.
 */
public enum RolloutStrategy {
    IMMEDIATE,
    STAGED,
    CANARY,
    BLUE_GREEN
}
