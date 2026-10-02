package com.secretvault.cli.auth;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.AuthDtos;
import com.secretvault.cli.security.RedactionHelper;

/**
 * Human Authentication Provider communicating with backend /api/v1/auth/login.
 */
public class HumanAuthenticationProvider implements AuthenticationProvider {

    @Override
    public AuthDtos.AuthResponse authenticate(SecretVaultApiClient apiClient, String email, char[] password) {
        String pwdStr = new String(password);
        try {
            return apiClient.login(email, pwdStr);
        } finally {
            RedactionHelper.wipe(password);
        }
    }

    @Override
    public String getAuthType() {
        return "PASSWORD";
    }
}
