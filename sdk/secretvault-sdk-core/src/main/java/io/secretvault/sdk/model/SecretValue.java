package io.secretvault.sdk.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable in-memory representation of a decrypted SecretVault secret.
 * 
 * <p>SECURITY GUARANTEES:
 * <ul>
 *   <li>{@link #toString()} ALWAYS redacts the secret payload (outputs {@code [REDACTED]}).</li>
 *   <li>Provides {@link #asCharArray()} and {@link #asByteArray()} for safer JVM memory handling.</li>
 *   <li>Payloads are never serialized to disk or printed in standard telemetry.</li>
 * </ul>
 */
public final class SecretValue {

    private final String name;
    private final String value;
    private final int version;
    private final String environment;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant expiresAt;
    private final Map<String, String> tags;

    public SecretValue(
            String name,
            String value,
            int version,
            String environment,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt,
            Map<String, String> tags
    ) {
        this.name = Objects.requireNonNull(name, "Secret name cannot be null");
        this.value = Objects.requireNonNull(value, "Secret value cannot be null");
        this.version = version;
        this.environment = environment != null ? environment : "default";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.expiresAt = expiresAt;
        this.tags = tags != null ? Collections.unmodifiableMap(tags) : Collections.emptyMap();
    }

    public static SecretValue of(String name, String value, int version, String environment) {
        return new SecretValue(name, value, version, environment, Instant.now(), Instant.now(), null, Collections.emptyMap());
    }

    public String name() {
        return name;
    }

    /**
     * Retrieves the plaintext secret value as a String.
     * Note: Applications are responsible for ensuring this value is not exposed in logs or telemetry.
     */
    public String value() {
        return value;
    }

    /**
     * Retrieves the plaintext secret value as a char array.
     */
    public char[] asCharArray() {
        return value.toCharArray();
    }

    /**
     * Retrieves the plaintext secret value as UTF-8 encoded bytes.
     */
    public byte[] asByteArray() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public int version() {
        return version;
    }

    public String environment() {
        return environment;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Map<String, String> tags() {
        return tags;
    }

    public boolean isExpired() {
        return isExpiredAt(Instant.now());
    }

    public boolean isExpiredAt(Instant instant) {
        return expiresAt != null && instant.isAfter(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecretValue that = (SecretValue) o;
        return version == that.version &&
                Objects.equals(name, that.name) &&
                Objects.equals(environment, that.environment) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, version, environment);
    }

    @Override
    public String toString() {
        return "SecretValue{" +
                "name='" + name + '\'' +
                ", version=" + version +
                ", environment='" + environment + '\'' +
                ", value=[REDACTED]" +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
