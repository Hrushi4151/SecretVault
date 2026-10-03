package io.secretvault.sdk.auth;

import java.util.Objects;

/**
 * Supplies an immutable pre-issued Bearer token.
 */
public class StaticTokenProvider implements CredentialsProvider {

    private final String token;

    public StaticTokenProvider(String token) {
        this.token = Objects.requireNonNull(token, "Token cannot be null").trim();
        if (this.token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be blank");
        }
    }

    @Override
    public String getBearerToken() {
        return token;
    }

    @Override
    public String toString() {
        return "StaticTokenProvider{token=[REDACTED]}";
    }
}
