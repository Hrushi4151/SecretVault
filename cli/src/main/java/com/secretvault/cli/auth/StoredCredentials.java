package com.secretvault.cli.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredCredentials(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        String serverUrl,
        String userEmail,
        UUID userId
) {
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        // Buffer 30 seconds before actual expiration
        return Instant.now().plusSeconds(30).isAfter(expiresAt);
    }
}
