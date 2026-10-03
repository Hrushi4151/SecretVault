package io.secretvault.sdk.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Supplies a SecretVault Machine Identity session token.
 */
public class MachineTokenProvider implements CredentialsProvider {

    private final UUID machineId;
    private final String machineToken;

    public MachineTokenProvider(UUID machineId, String machineToken) {
        this.machineId = machineId;
        this.machineToken = Objects.requireNonNull(machineToken, "Machine token cannot be null").trim();
    }

    public UUID getMachineId() {
        return machineId;
    }

    @Override
    public String getBearerToken() {
        return machineToken;
    }

    @Override
    public String toString() {
        return "MachineTokenProvider{machineId=" + machineId + ", token=[REDACTED]}";
    }
}
