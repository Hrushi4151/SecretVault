package com.secretvault.oidc.dto;

import com.secretvault.machine.dto.MachineDtos;
import com.secretvault.oidc.entity.OidcClaimRule;
import com.secretvault.oidc.entity.OidcProvider;
import com.secretvault.oidc.entity.OidcTrustPolicy;
import com.secretvault.oidc.model.OidcClaimOperator;
import com.secretvault.oidc.model.OidcProviderStatus;
import com.secretvault.oidc.model.OidcProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OidcDtos {

    public record CreateOidcProviderRequest(
            @NotBlank(message = "Provider name is required")
            @Size(min = 2, max = 128)
            String name,

            @NotBlank(message = "Issuer URL is required")
            String issuer,

            String discoveryUrl,
            String jwksUrl,

            @NotBlank(message = "Audience is required")
            String audience,

            OidcProviderType providerType,
            String allowedAlgorithms
    ) {}

    public record UpdateOidcProviderRequest(
            String name,
            String issuer,
            String discoveryUrl,
            String jwksUrl,
            String audience,
            OidcProviderType providerType,
            String allowedAlgorithms
    ) {}

    public record OidcProviderResponse(
            UUID id,
            UUID workspaceId,
            String name,
            String issuer,
            String discoveryUrl,
            String jwksUrl,
            String audience,
            OidcProviderType providerType,
            OidcProviderStatus status,
            String allowedAlgorithms,
            Instant lastJwksRefreshAt,
            Instant lastAuthenticatedAt,
            long successCount,
            long failureCount,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static OidcProviderResponse fromEntity(OidcProvider entity) {
            return new OidcProviderResponse(
                    entity.getId(),
                    entity.getWorkspaceId(),
                    entity.getName(),
                    entity.getIssuer(),
                    entity.getDiscoveryUrl(),
                    entity.getJwksUrl(),
                    entity.getAudience(),
                    entity.getProviderType(),
                    entity.getStatus(),
                    entity.getAllowedAlgorithms(),
                    entity.getLastJwksRefreshAt(),
                    entity.getLastAuthenticatedAt(),
                    entity.getSuccessCount(),
                    entity.getFailureCount(),
                    entity.getCreatedBy(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }

    public record ClaimRuleDto(
            UUID id,
            @NotBlank String claimName,
            @NotNull OidcClaimOperator operator,
            @NotBlank String expectedValue
    ) {}

    public record CreateTrustPolicyRequest(
            @NotNull UUID oidcProviderId,
            @NotBlank @Size(min = 2, max = 128) String name,
            String description,
            Boolean enabled,
            Integer priority,
            List<ClaimRuleDto> claimRules
    ) {}

    public record UpdateTrustPolicyRequest(
            String name,
            String description,
            Boolean enabled,
            Integer priority,
            List<ClaimRuleDto> claimRules
    ) {}

    public record OidcTrustPolicyResponse(
            UUID id,
            UUID workspaceId,
            UUID machineIdentityId,
            UUID oidcProviderId,
            String providerName,
            String name,
            String description,
            boolean enabled,
            int priority,
            List<ClaimRuleDto> claimRules,
            String naturalLanguageSummary,
            boolean isProductionPolicy,
            boolean isBroadPolicy,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record OidcTokenExchangeRequest(
            UUID providerId,
            String issuer,
            @NotBlank(message = "OIDC token is required") String token
    ) {}

    public record OidcTokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            MachineDtos.MachineIdentityResponse machineIdentity
    ) {}
}
