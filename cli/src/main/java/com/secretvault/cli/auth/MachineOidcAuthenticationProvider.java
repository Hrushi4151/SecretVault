package com.secretvault.cli.auth;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.AuthDtos;

import java.util.UUID;

/**
 * Machine Workload OIDC Authentication Provider implementation for Phase 9 integration.
 */
public class MachineOidcAuthenticationProvider implements AuthenticationProvider {

    @Override
    public AuthDtos.AuthResponse authenticate(SecretVaultApiClient apiClient, String issuerOrProviderId, char[] tokenChars) {
        String token = tokenChars != null ? new String(tokenChars) : "";
        UUID providerId = null;
        String issuer = null;

        try {
            providerId = UUID.fromString(issuerOrProviderId);
        } catch (IllegalArgumentException e) {
            issuer = issuerOrProviderId;
        }

        AuthDtos.OidcTokenResponse oidcResp = apiClient.exchangeOidcToken(issuer, providerId, token);

        AuthDtos.UserResponse user = new AuthDtos.UserResponse(
                oidcResp.machineIdentity() != null ? oidcResp.machineIdentity().id() : null,
                oidcResp.machineIdentity() != null ? "machine:" + oidcResp.machineIdentity().name() : "machine",
                oidcResp.machineIdentity() != null ? "Machine (" + oidcResp.machineIdentity().name() + ")" : "Machine Identity",
                false,
                oidcResp.machineIdentity() != null ? oidcResp.machineIdentity().status() : "ACTIVE",
                null
        );

        return new AuthDtos.AuthResponse(
                oidcResp.accessToken(),
                null,
                oidcResp.tokenType(),
                oidcResp.expiresIn(),
                user,
                null,
                false,
                null,
                null
        );
    }

    @Override
    public String getAuthType() {
        return "OIDC";
    }
}
