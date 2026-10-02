package com.secretvault.security.event.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Enforces strict security boundaries on event metadata.
 * Strips any sensitive fields (tokens, secrets, credentials, passwords, JWTs)
 * and serializes only safe, bounded key-value attributes.
 */
public final class SafeEventMetadataSanitizer {

    private static final Logger log = LoggerFactory.getLogger(SafeEventMetadataSanitizer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "value", "secret", "secretvalue", "plaintext", "token", "accesstoken",
            "refreshtoken", "password", "key", "masterkey", "dek", "kek", "authorization",
            "cookie", "session", "jwt", "privatekey", "credentials", "ciphertext"
    );

    private static final int MAX_ENTRY_LENGTH = 1000;
    private static final int MAX_ENTRIES = 25;

    private SafeEventMetadataSanitizer() {
    }

    public static String sanitizeAndSerialize(Map<String, ?> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        Map<String, Object> clean = new HashMap<>();
        int count = 0;
        for (Map.Entry<String, ?> entry : input.entrySet()) {
            if (count++ >= MAX_ENTRIES) {
                break;
            }
            String rawKey = entry.getKey();
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            String normalizedKey = rawKey.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "");
            if (isForbidden(normalizedKey)) {
                clean.put(rawKey, "[REDACTED_SENSITIVE_FIELD]");
                continue;
            }

            Object val = entry.getValue();
            if (val == null) {
                continue;
            }
            String strVal = String.valueOf(val);
            if (strVal.length() > MAX_ENTRY_LENGTH) {
                strVal = strVal.substring(0, MAX_ENTRY_LENGTH) + "...[TRUNCATED]";
            }
            clean.put(rawKey, strVal);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(clean);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize sanitized security event metadata: {}", e.getMessage());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static boolean isForbidden(String key) {
        for (String forbidden : FORBIDDEN_KEYS) {
            if (key.contains(forbidden)) {
                return true;
            }
        }
        return false;
    }
}
