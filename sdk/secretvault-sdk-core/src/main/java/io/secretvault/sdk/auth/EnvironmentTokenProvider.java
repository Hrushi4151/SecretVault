package io.secretvault.sdk.auth;

import io.secretvault.sdk.exception.AuthenticationException;
import io.secretvault.sdk.exception.ErrorCode;

import java.util.List;

/**
 * Resolves authentication token from process environment variables.
 */
public class EnvironmentTokenProvider implements CredentialsProvider {

    private static final List<String> ENV_KEYS = List.of(
            "SECRET_VAULT_TOKEN",
            "SECRETVAULT_TOKEN",
            "SECRET_VAULT_MACHINE_TOKEN",
            "SECRET_VAULT_ACCESS_TOKEN"
    );

    @Override
    public String getBearerToken() {
        for (String key : ENV_KEYS) {
            String value = System.getenv(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        throw new AuthenticationException(
                "No authentication token found in environment variables (checked " + ENV_KEYS + ")",
                ErrorCode.SV_AUTH_REQUIRED
        );
    }

    public boolean hasToken() {
        for (String key : ENV_KEYS) {
            String value = System.getenv(key);
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "EnvironmentTokenProvider{token=[REDACTED]}";
    }
}
