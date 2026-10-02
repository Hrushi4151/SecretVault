package com.secretvault.sync.model;

import java.util.UUID;

/**
 * An individual planned operation within a sync plan (zero secret plaintext).
 */
public record SyncOperationPlan(
        UUID secretId,
        String secretName,
        UUID mappingId,
        UUID integrationId,
        SyncOperationType operationType,
        SyncOperationStatus initialStatus,
        String desiredFingerprint,
        String observedFingerprint,
        String reason,
        String errorCode,
        String errorMessage
) {}
