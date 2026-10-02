package com.secretvault.sync.model;

/**
 * Deterministic severity classification for detected secret drift.
 */
public enum DriftSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}
