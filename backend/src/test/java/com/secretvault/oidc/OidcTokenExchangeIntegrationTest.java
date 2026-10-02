package com.secretvault.oidc;

import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.model.MachineType;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.machine.service.MachineSessionService;
import com.secretvault.oidc.adapter.ClaimAdapterRegistry;
import com.secretvault.oidc.adapter.GenericOidcClaimAdapter;
import com.secretvault.oidc.adapter.GitHubActionsClaimAdapter;
import com.secretvault.oidc.adapter.GitLabCiClaimAdapter;
import com.secretvault.oidc.dto.OidcDtos;
import com.secretvault.oidc.entity.OidcClaimRule;
import com.secretvault.oidc.entity.OidcProvider;
import com.secretvault.oidc.entity.OidcTrustPolicy;
import com.secretvault.oidc.model.OidcClaimOperator;
import com.secretvault.oidc.model.OidcProviderType;
import com.secretvault.oidc.repository.OidcProviderRepository;
import com.secretvault.oidc.repository.OidcTrustPolicyRepository;
import com.secretvault.oidc.security.ClaimRuleEngine;
import com.secretvault.oidc.security.JwtValidationEngine;
import com.secretvault.oidc.service.OidcTokenExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class OidcTokenExchangeIntegrationTest {

    private OidcProviderRepository providerRepository;
    private MachineIdentityRepository machineRepository;
    private OidcTrustPolicyRepository trustPolicyRepository;
    private MachineSessionService sessionService;
    private JwtValidationEngine jwtValidationEngine;
    private ClaimRuleEngine claimRuleEngine;
    private ClaimAdapterRegistry adapterRegistry;
    private AuditService auditService;

    private OidcTokenExchangeService exchangeService;

    private UUID workspaceId;
    private OidcProvider provider;
    private MachineIdentity activeMachine;
    private MachineIdentity disabledMachine;
    private OidcTrustPolicy policy1;

    @BeforeEach
    void setUp() {
        providerRepository = Mockito.mock(OidcProviderRepository.class);
        machineRepository = Mockito.mock(MachineIdentityRepository.class);
        trustPolicyRepository = Mockito.mock(OidcTrustPolicyRepository.class);
        sessionService = Mockito.mock(MachineSessionService.class);
        jwtValidationEngine = Mockito.mock(JwtValidationEngine.class);
        claimRuleEngine = new ClaimRuleEngine();
        adapterRegistry = new ClaimAdapterRegistry(
                new GitHubActionsClaimAdapter(),
                new GitLabCiClaimAdapter(),
                new GenericOidcClaimAdapter()
        );
        auditService = Mockito.mock(AuditService.class);

        exchangeService = new OidcTokenExchangeService(
                providerRepository,
                trustPolicyRepository,
                machineRepository,
                sessionService,
                jwtValidationEngine,
                claimRuleEngine,
                adapterRegistry,
                auditService
        );

        workspaceId = UUID.randomUUID();

        provider = new OidcProvider();
        provider.setId(UUID.randomUUID());
        provider.setWorkspaceId(workspaceId);
        provider.setName("GitHub Actions");
        provider.setProviderType(OidcProviderType.GITHUB_ACTIONS);
        provider.setIssuer("https://token.actions.githubusercontent.com");
        provider.setAudience("https://github.com/secretvault");

        activeMachine = new MachineIdentity(workspaceId, "github-rally-ci", "CI", MachineType.CI_CD, null, null);
        activeMachine.setId(UUID.randomUUID());
        activeMachine.setStatus(MachineStatus.ACTIVE);

        disabledMachine = new MachineIdentity(workspaceId, "disabled-worker", "Worker", MachineType.WORKLOAD, null, null);
        disabledMachine.setId(UUID.randomUUID());
        disabledMachine.setStatus(MachineStatus.DISABLED);

        OidcClaimRule rule1 = new OidcClaimRule(UUID.randomUUID(), "repository", OidcClaimOperator.EQUALS, "Hrushi4151/Rally");
        OidcClaimRule rule2 = new OidcClaimRule(UUID.randomUUID(), "ref", OidcClaimOperator.EQUALS, "refs/heads/main");

        policy1 = new OidcTrustPolicy();
        policy1.setId(UUID.randomUUID());
        policy1.setWorkspaceId(workspaceId);
        policy1.setMachineIdentityId(activeMachine.getId());
        policy1.setOidcProviderId(provider.getId());
        policy1.setEnabled(true);
        policy1.setClaimRules(List.of(rule1, rule2));

        when(providerRepository.findById(eq(provider.getId()))).thenReturn(Optional.of(provider));
        when(machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(eq(activeMachine.getId()), eq(workspaceId))).thenReturn(Optional.of(activeMachine));
        when(machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(eq(disabledMachine.getId()), eq(workspaceId))).thenReturn(Optional.of(disabledMachine));
    }

    @Test
    @DisplayName("Successful OIDC token exchange returns active machine session token")
    void testSuccessfulTokenExchange() {
        String mockJwt = "mock.jwt.token";
        Map<String, Object> claims = Map.of(
                "iss", "https://token.actions.githubusercontent.com",
                "aud", "https://github.com/secretvault",
                "repository", "Hrushi4151/Rally",
                "ref", "refs/heads/main"
        );

        when(jwtValidationEngine.validateAndExtractClaims(eq(mockJwt), eq(provider))).thenReturn(claims);
        when(trustPolicyRepository.findActivePoliciesByWorkspaceAndProvider(eq(workspaceId), eq(provider.getId()))).thenReturn(List.of(policy1));
        when(sessionService.createSession(eq(workspaceId), eq(activeMachine.getId()), any(), any(), any(), any(), anyLong())).thenReturn(
                new MachineSessionService.MachineTokenResult("sv_machine_test123", "sv_machine_test", Instant.now().plusSeconds(600), 600, activeMachine)
        );

        OidcDtos.OidcTokenExchangeRequest request = new OidcDtos.OidcTokenExchangeRequest(provider.getId(), null, mockJwt);
        OidcDtos.OidcTokenResponse response = exchangeService.exchangeToken(request, "127.0.0.1", "SecretVault-Runner");

        assertNotNull(response);
        assertEquals("sv_machine_test123", response.accessToken());
        assertEquals(600, response.expiresIn());
        assertNotNull(response.machineIdentity());
        assertEquals("github-rally-ci", response.machineIdentity().name());
    }

    @Test
    @DisplayName("Reject OIDC exchange when claim conditions mismatch")
    void testClaimMismatchRejection() {
        String mockJwt = "mock.jwt.token";
        Map<String, Object> wrongBranchClaims = Map.of(
                "iss", "https://token.actions.githubusercontent.com",
                "aud", "https://github.com/secretvault",
                "repository", "Hrushi4151/Rally",
                "ref", "refs/heads/feature-branch" // Not main!
        );

        when(jwtValidationEngine.validateAndExtractClaims(eq(mockJwt), eq(provider))).thenReturn(wrongBranchClaims);
        when(trustPolicyRepository.findActivePoliciesByWorkspaceAndProvider(eq(workspaceId), eq(provider.getId()))).thenReturn(List.of(policy1));

        OidcDtos.OidcTokenExchangeRequest request = new OidcDtos.OidcTokenExchangeRequest(provider.getId(), null, mockJwt);

        ApiException ex = assertThrows(ApiException.class, () ->
                exchangeService.exchangeToken(request, "127.0.0.1", "SecretVault-Runner"));
        assertTrue(ex.getMessage().contains("no matching trust policy"));
    }

    @Test
    @DisplayName("Reject OIDC exchange when machine identity is DISABLED")
    void testDisabledMachineRejection() {
        String mockJwt = "mock.jwt.token";
        Map<String, Object> claims = Map.of(
                "iss", "https://token.actions.githubusercontent.com",
                "repository", "Hrushi4151/Rally",
                "ref", "refs/heads/main"
        );

        OidcTrustPolicy disabledPolicy = new OidcTrustPolicy();
        disabledPolicy.setId(UUID.randomUUID());
        disabledPolicy.setWorkspaceId(workspaceId);
        disabledPolicy.setMachineIdentityId(disabledMachine.getId());
        disabledPolicy.setOidcProviderId(provider.getId());
        disabledPolicy.setEnabled(true);
        disabledPolicy.setClaimRules(policy1.getClaimRules());

        when(jwtValidationEngine.validateAndExtractClaims(eq(mockJwt), eq(provider))).thenReturn(claims);
        when(trustPolicyRepository.findActivePoliciesByWorkspaceAndProvider(eq(workspaceId), eq(provider.getId()))).thenReturn(List.of(disabledPolicy));

        OidcDtos.OidcTokenExchangeRequest request = new OidcDtos.OidcTokenExchangeRequest(provider.getId(), null, mockJwt);

        ApiException ex = assertThrows(ApiException.class, () ->
                exchangeService.exchangeToken(request, "127.0.0.1", "SecretVault-Runner"));
        assertTrue(ex.getMessage().contains("DISABLED"));
    }

    @Test
    @DisplayName("Reject OIDC exchange when multiple ambiguous policies match distinct machines")
    void testAmbiguousMatchingRejection() {
        String mockJwt = "mock.jwt.token";
        Map<String, Object> claims = Map.of(
                "iss", "https://token.actions.githubusercontent.com",
                "repository", "Hrushi4151/Rally",
                "ref", "refs/heads/main"
        );

        MachineIdentity otherMachine = new MachineIdentity(workspaceId, "duplicate-matching-machine", "Dupe", MachineType.CI_CD, null, null);
        otherMachine.setId(UUID.randomUUID());
        otherMachine.setStatus(MachineStatus.ACTIVE);

        OidcTrustPolicy policy2 = new OidcTrustPolicy();
        policy2.setId(UUID.randomUUID());
        policy2.setWorkspaceId(workspaceId);
        policy2.setMachineIdentityId(otherMachine.getId());
        policy2.setOidcProviderId(provider.getId());
        policy2.setEnabled(true);
        policy2.setClaimRules(policy1.getClaimRules()); // Identical matching rules

        when(jwtValidationEngine.validateAndExtractClaims(eq(mockJwt), eq(provider))).thenReturn(claims);
        when(trustPolicyRepository.findActivePoliciesByWorkspaceAndProvider(eq(workspaceId), eq(provider.getId()))).thenReturn(List.of(policy1, policy2));

        OidcDtos.OidcTokenExchangeRequest request = new OidcDtos.OidcTokenExchangeRequest(provider.getId(), null, mockJwt);

        ApiException ex = assertThrows(ApiException.class, () ->
                exchangeService.exchangeToken(request, "127.0.0.1", "SecretVault-Runner"));
        assertTrue(ex.getMessage().contains("ambiguous trust policy"));
    }
}
