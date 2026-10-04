package io.secretvault.sdk.consumer;

import java.time.Instant;

/**
 * Result of an individual background consumer heartbeat execution.
 */
public record ConsumerHeartbeatResult(
        boolean success,
        Integer acknowledgedVersion,
        Instant timestamp,
        String errorMessage
) {
    public static ConsumerHeartbeatResult success(Integer acknowledgedVersion) {
        return new ConsumerHeartbeatResult(true, acknowledgedVersion, Instant.now(), null);
    }

    public static ConsumerHeartbeatResult failure(Integer acknowledgedVersion, String errorMessage) {
        return new ConsumerHeartbeatResult(false, acknowledgedVersion, Instant.now(), errorMessage);
    }
}
