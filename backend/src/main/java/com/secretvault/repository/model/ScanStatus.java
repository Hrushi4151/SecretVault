package com.secretvault.repository.model;

public enum ScanStatus {
    QUEUED,
    CLONING,
    INDEXING,
    SCANNING,
    CLASSIFYING,
    VALIDATING,
    FINALIZING,
    COMPLETED,
    FAILED,
    CANCELLED
}
