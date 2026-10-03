package io.secretvault.sdk.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionUtilTest {

    @Test
    @DisplayName("RedactionUtil masks Bearer tokens and sensitive key values")
    void testRedaction() {
        String logLine = "Sending request with Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIn0.xyz and accessToken=my_secret_token_123";
        String sanitized = RedactionUtil.redact(logLine);

        assertThat(sanitized).contains("Authorization: Bearer [REDACTED]");
        assertThat(sanitized).contains("accessToken=[REDACTED]");
        assertThat(sanitized).doesNotContain("my_secret_token_123");
    }
}
