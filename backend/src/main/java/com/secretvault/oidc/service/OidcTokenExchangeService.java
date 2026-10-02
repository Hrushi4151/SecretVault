package com.secretvault.oidc.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.machine.dto.MachineDtos;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.machine.service.MachineSessionService;
import com.secretvault.oidc.adapter.ClaimAdapter;
import com.secretvault.oidc.adapter.ClaimAdapterRegistry;
import com.secretvault.oidc.dto.OidcDtos;
import com.secretvault.oidc.entity.OidcProvider;
import com.secretvault.oidc.entity.OidcTrustPolicy;
import com.secretvault.oidc.model.OidcProviderStatus;
import com.secretvault.oidc.repository.OidcProviderRepository;
import com.secretvault.oidc.repository.OidcTrustPolicyRepository;
import com.secretvault.oidc.security.ClaimRuleEngine;
import com.secretvault.oidc.security.JwtValidationEngine;
import com.secretvault.oidc.security.OidcDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OidcTokenExchangeService {

    private static final Logger log = LoggerFactory.getLogger(OidcTokenExchangeService.class);

    private final OidcProviderRepository providerRepository;
    private final OidcTrustPolicyRepository trustPolicyRepository;
    private final MachineIdentityRepository machineRepository;
    private final MachineSessionService machineSessionService;
    private final JwtValidationEngine jwtValidationEngine;
    private final ClaimRuleEngine claimRuleEngine;
    private final ClaimAdapterRegistry claimAdapterRegistry;
    private final AuditService auditService;

    public OidcTokenExchangeService(
            OidcProviderRepository providerRepository,
            OidcTrustPolicyRepository trustPolicyRepository,
            MachineIdentityRepository machineRepository,
            MachineSessionService machineSessionService,
            JwtValidationEngine jwtValidationEngine,
            ClaimRuleEngine claimRuleEngine,
            ClaimAdapterRegistry claimAdapterRegistry,
            AuditService auditService
    ) {
        this.providerRepository = providerRepository;
        this.trustPolicyRepository = trustPolicyRepository;
        this.machineRepository = machineRepository;
        this.machineSessionService = machineSessionService;
        this.jwtValidationEngine = jwtValidationEngine;
        this.claimRuleEngine = claimRuleEngine;
        this.claimAdapterRegistry = claimAdapterRegistry;
        this.auditService = auditService;
    }

    /**
     * Authenticates an external CI/CD workload via OIDC token and issues a short-lived SecretVault machine token.
     */
    @Transactional
    public OidcDtos.OidcTokenResponse exchangeToken(
            OidcDtos.OidcTokenExchangeRequest request,
            String sourceIp,
            String userAgent
    ) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw ApiException.unauthorized("OIDC token is required");
        }

        // 1. Locate Target OIDC Provider
        OidcProvider provider = resolveProvider(request);
        if (provider == null || provider.getStatus() != OidcProviderStatus.ACTIVE) {
            log.warn("OIDC token exchange rejected: Provider not found or inactive (providerId={}, issuer={})",
                    request.providerId(), request.issuer());
            throw ApiException.unauthorized("OIDC authentication failed: provider not found or inactive");
        }

        // 2. Cryptographic JWT Validation
        Map<String, Object> rawClaims;
        try {
            rawClaims = jwtValidationEngine.validateAndExtractClaims(request.token(), provider);
        } catch (ApiException e) {
            provider.setFailureCount(provider.getFailureCount() + 1);
            providerRepository.save(provider);

            auditService.recordAudit(
                    null,
                    provider.getWorkspaceId(),
                    null,
                    "CI_CD_WORKLOAD",
                    AuditAction.OIDC_AUTH_FAILURE,
                    "OIDC_PROVIDER",
                    provider.getId(),
                    null,
                    sourceIp,
                    "FAILED: " + e.getMessage()
            );
            throw e;
        }

        // 3. Provider-Specific Claim Adaptation
        ClaimAdapter adapter = claimAdapterRegistry.getAdapter(provider.getProviderType());
        Map<String, Object> adaptedClaims = adapter.adaptClaims(rawClaims);

        // 4. Evaluate Trust Policies in Workspace
        List<OidcTrustPolicy> activePolicies = trustPolicyRepository.findActivePoliciesByWorkspaceAndProvider(
                provider.getWorkspaceId(), provider.getId()
        );

        Set<UUID> matchingMachineIds = new HashSet<>();
        OidcTrustPolicy matchedPolicy = null;

        for (OidcTrustPolicy policy : activePolicies) {
            if (claimRuleEngine.matchesAll(policy.getClaimRules(), adaptedClaims)) {
                matchingMachineIds.add(policy.getMachineIdentityId());
                if (matchedPolicy == null) {
                    matchedPolicy = policy;
                }
            }
        }

        // 5. Policy Matching & Ambiguity Check
        if (matchingMachineIds.isEmpty()) {
            log.warn("No OIDC trust policy matched claims for provider [{}] in workspace [{}]",
                    provider.getName(), provider.getWorkspaceId());
            provider.setFailureCount(provider.getFailureCount() + 1);
            providerRepository.save(provider);

            auditService.recordAudit(
                    null,
                    provider.getWorkspaceId(),
                    null,
                    "CI_CD_WORKLOAD",
                    AuditAction.OIDC_AUTH_FAILURE,
                    "OIDC_PROVIDER",
                    provider.getId(),
                    null,
                    sourceIp,
                    "FAILED: OIDC_CLAIM_MISMATCH"
            );
            throw ApiException.unauthorized("OIDC authentication failed: no matching trust policy");
        }

        if (matchingMachineIds.size() > 1) {
            log.warn("Ambiguous OIDC trust policy match: multiple machine identities matched for provider [{}]",
                    provider.getName());
            provider.setFailureCount(provider.getFailureCount() + 1);
            providerRepository.save(provider);

            auditService.recordAudit(
                    null,
                    provider.getWorkspaceId(),
                    null,
                    "CI_CD_WORKLOAD",
                    AuditAction.OIDC_AUTH_FAILURE,
                    "OIDC_PROVIDER",
                    provider.getId(),
                    null,
                    sourceIp,
                    "FAILED: OIDC_TRUST_POLICY_AMBIGUOUS"
            );
            throw ApiException.unauthorized("OIDC authentication failed: ambiguous trust policy resolution");
        }

        UUID resolvedMachineId = matchingMachineIds.iterator().next();

        // 6. Validate Machine Identity Status
        MachineIdentity machine = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(
                resolvedMachineId, provider.getWorkspaceId()
        ).orElseThrow(() -> ApiException.unauthorized("OIDC authentication failed: machine identity not found"));

        Instant now = Instant.now();
        if (!machine.isUsable(now)) {
            provider.setFailureCount(provider.getFailureCount() + 1);
            providerRepository.save(provider);

            auditService.recordAudit(
                    null,
                    provider.getWorkspaceId(),
                    machine.getId(),
                    "MACHINE_IDENTITY",
                    AuditAction.OIDC_AUTH_FAILURE,
                    "MACHINE_IDENTITY",
                    machine.getId(),
                    null,
                    sourceIp,
                    "FAILED: Machine status is " + machine.getStatus()
            );
            throw ApiException.unauthorized("OIDC authentication failed: machine identity is " + machine.getStatus());
        }

        // 7. Issue Short-Lived Machine Session (900 seconds / 15 minutes TTL)
        MachineSessionService.MachineTokenResult tokenResult = machineSessionService.createSession(
                provider.getWorkspaceId(),
                machine.getId(),
                provider.getId(),
                sourceIp,
                userAgent,
                Map.of(
                        "issuer", provider.getIssuer(),
                        "providerType", provider.getProviderType().name(),
                        "trustPolicyId", matchedPolicy != null ? matchedPolicy.getId().toString() : ""
                ),
                900
        );

        // Update provider stats
        provider.setSuccessCount(provider.getSuccessCount() + 1);
        provider.setLastAuthenticatedAt(now);
        providerRepository.save(provider);

        auditService.recordAudit(
                null,
                provider.getWorkspaceId(),
                machine.getId(),
                "MACHINE_IDENTITY",
                AuditAction.OIDC_AUTH_SUCCESS,
                "MACHINE_IDENTITY",
                machine.getId(),
                null,
                sourceIp,
                "SUCCESS (Policy: " + (matchedPolicy != null ? matchedPolicy.getName() : "Direct") + ")"
        );

        log.info("Successfully authenticated machine [{}] via OIDC provider [{}]",
                machine.getName(), provider.getName());

        return new OidcDtos.OidcTokenResponse(
                tokenResult.rawToken(),
                "Bearer",
                tokenResult.expiresInSeconds(),
                MachineDtos.MachineIdentityResponse.fromEntity(machine)
        );
    }

    private OidcProvider resolveProvider(OidcDtos.OidcTokenExchangeRequest request) {
        if (request.providerId() != null) {
            return providerRepository.findById(request.providerId()).orElse(null);
        }
        if (request.issuer() != null && !request.issuer().isBlank()) {
            String normIssuer = OidcDiscoveryService.normalizeIssuer(request.issuer());
            List<OidcProvider> list = providerRepository.findActiveByIssuerIgnoreCase(normIssuer);
            if (!list.isEmpty()) {
                return list.get(0);
            }
        }
        return null;
    }
}
