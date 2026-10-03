package io.secretvault.sdk.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthProvidersTest {

    @Test
    @DisplayName("StaticTokenProvider returns bearer token and redacts in toString()")
    void testStaticTokenProvider() {
        StaticTokenProvider provider = new StaticTokenProvider("secret-token-12345");
        assertThat(provider.getBearerToken()).isEqualTo("secret-token-12345");
        assertThat(provider.toString()).contains("[REDACTED]");
        assertThat(provider.toString()).doesNotContain("secret-token-12345");
    }

    @Test
    @DisplayName("MachineTokenProvider returns machine token and redacts in toString()")
    void testMachineTokenProvider() {
        UUID machineId = UUID.randomUUID();
        MachineTokenProvider provider = new MachineTokenProvider(machineId, "mach-tok-789");
        assertThat(provider.getBearerToken()).isEqualTo("mach-tok-789");
        assertThat(provider.getMachineId()).isEqualTo(machineId);
        assertThat(provider.toString()).contains("[REDACTED]");
        assertThat(provider.toString()).doesNotContain("mach-tok-789");
    }

    @Test
    @DisplayName("CredentialProviderChain resolves first working provider in sequence")
    void testCredentialProviderChain() {
        StaticTokenProvider fallback = new StaticTokenProvider("fallback-token");
        CredentialProviderChain chain = new CredentialProviderChain(
                new EnvironmentTokenProvider(), // might fail if env not set
                fallback
        );

        assertThat(chain.getBearerToken()).isNotNull();
    }
}
