package io.secretvault.sdk.consumer;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuration for the SDK background consumer heartbeat daemon.
 *
 * <p><strong>Security Invariant:</strong>
 * Heartbeat communication contains ONLY consumer runtime identity and version acknowledgement.
 * Under no circumstances does it transmit secret plaintext, keys, tokens, or credentials.
 */
public final class ConsumerHeartbeatConfig {

    private final UUID workspaceId;
    private final UUID consumerId;
    private final Duration interval;
    private final String sdkVersion;
    private final String runtimeFramework;
    private final Integer initialAcknowledgedVersion;
    private final boolean enabled;
    private final int maxRetries;

    private ConsumerHeartbeatConfig(Builder builder) {
        this.workspaceId = builder.workspaceId;
        this.consumerId = builder.consumerId;
        this.interval = builder.interval != null ? builder.interval : Duration.ofSeconds(30);
        this.sdkVersion = builder.sdkVersion != null ? builder.sdkVersion : "1.0.0";
        this.runtimeFramework = builder.runtimeFramework != null ? builder.runtimeFramework : "Java " + System.getProperty("java.version");
        this.initialAcknowledgedVersion = builder.initialAcknowledgedVersion;
        this.enabled = builder.enabled;
        this.maxRetries = builder.maxRetries > 0 ? builder.maxRetries : 3;

        validate();
    }

    private void validate() {
        if (enabled && consumerId == null) {
            throw new IllegalArgumentException("consumerId is required when heartbeat is enabled");
        }
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("heartbeat interval must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getConsumerId() { return consumerId; }
    public Duration getInterval() { return interval; }
    public String getSdkVersion() { return sdkVersion; }
    public String getRuntimeFramework() { return runtimeFramework; }
    public Integer getInitialAcknowledgedVersion() { return initialAcknowledgedVersion; }
    public boolean isEnabled() { return enabled; }
    public int getMaxRetries() { return maxRetries; }

    public static final class Builder {
        private UUID workspaceId;
        private UUID consumerId;
        private Duration interval = Duration.ofSeconds(30);
        private String sdkVersion = "1.0.0";
        private String runtimeFramework = "Java " + System.getProperty("java.version");
        private Integer initialAcknowledgedVersion;
        private boolean enabled = true;
        private int maxRetries = 3;

        public Builder workspaceId(UUID workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder consumerId(UUID consumerId) {
            this.consumerId = consumerId;
            return this;
        }

        public Builder interval(Duration interval) {
            this.interval = interval;
            return this;
        }

        public Builder sdkVersion(String sdkVersion) {
            this.sdkVersion = sdkVersion;
            return this;
        }

        public Builder runtimeFramework(String runtimeFramework) {
            this.runtimeFramework = runtimeFramework;
            return this;
        }

        public Builder initialAcknowledgedVersion(Integer initialAcknowledgedVersion) {
            this.initialAcknowledgedVersion = initialAcknowledgedVersion;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ConsumerHeartbeatConfig build() {
            return new ConsumerHeartbeatConfig(this);
        }
    }
}
