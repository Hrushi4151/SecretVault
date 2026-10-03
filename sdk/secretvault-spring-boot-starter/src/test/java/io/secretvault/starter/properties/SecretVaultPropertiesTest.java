package io.secretvault.starter.properties;

import io.secretvault.sdk.resilience.ResiliencePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SecretVaultPropertiesTest {

    @Test
    @DisplayName("SecretVaultProperties initializes with secure production defaults")
    void testDefaultProperties() {
        SecretVaultProperties props = new SecretVaultProperties();

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getWorkspace()).isEqualTo("default");
        assertThat(props.getEnvironment()).isEqualTo("development");
        assertThat(props.getCache().isEnabled()).isTrue();
        assertThat(props.getCache().getTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(props.getResilience().getMode()).isEqualTo(ResiliencePolicy.FAIL_CLOSED);
        assertThat(props.getResilience().getMaxStale()).isEqualTo(Duration.ofMinutes(5));
    }
}
