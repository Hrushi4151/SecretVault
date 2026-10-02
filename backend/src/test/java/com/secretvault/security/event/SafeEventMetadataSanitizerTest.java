package com.secretvault.security.event;

import com.secretvault.security.event.util.SafeEventMetadataSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SafeEventMetadataSanitizerTest {

    @Test
    @DisplayName("Should sanitize sensitive keys and preserve safe keys")
    void testSanitizeSensitiveKeys() {
        Map<String, Object> input = Map.of(
                "actionContext", "safeValue",
                "secretValue", "super_secret_password_123",
                "token", "bearer_jwt_token",
                "jwt", "eyJhbGciOiJIUzI1NiJ9",
                "resourceName", "PAYMENTS_DB_URL",
                "password", "p@ssword123",
                "role", "DEVELOPER"
        );

        String serialized = SafeEventMetadataSanitizer.sanitizeAndSerialize(input);
        assertNotNull(serialized);

        Map<String, Object> deserialized = SafeEventMetadataSanitizer.deserialize(serialized);
        assertEquals("safeValue", deserialized.get("actionContext"));
        assertEquals("PAYMENTS_DB_URL", deserialized.get("resourceName"));
        assertEquals("DEVELOPER", deserialized.get("role"));

        assertEquals("[REDACTED_SENSITIVE_FIELD]", deserialized.get("secretValue"));
        assertEquals("[REDACTED_SENSITIVE_FIELD]", deserialized.get("token"));
        assertEquals("[REDACTED_SENSITIVE_FIELD]", deserialized.get("jwt"));
        assertEquals("[REDACTED_SENSITIVE_FIELD]", deserialized.get("password"));
    }

    @Test
    @DisplayName("Should handle null or empty input gracefully")
    void testNullOrEmptyInput() {
        assertNull(SafeEventMetadataSanitizer.sanitizeAndSerialize(null));
        assertNull(SafeEventMetadataSanitizer.sanitizeAndSerialize(Map.of()));
        assertTrue(SafeEventMetadataSanitizer.deserialize(null).isEmpty());
        assertTrue(SafeEventMetadataSanitizer.deserialize("").isEmpty());
    }
}
