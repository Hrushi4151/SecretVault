package com.secretvault.provider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.dto.CreateProviderIntegrationRequest;
import com.secretvault.provider.dto.ProviderIntegrationResponse;
import com.secretvault.provider.dto.UpdateProviderIntegrationRequest;
import com.secretvault.provider.dto.ValidateIntegrationResponse;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderCapability;
import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.model.ProviderValidationResult;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.security.event.service.SecurityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderIntegrationServiceTest {

    @Mock
    private ProviderIntegrationRepository integrationRepository;
    @Mock
    private ProviderResourceMappingRepository mappingRepository;
    @Mock
    private ProviderAdapterRegistry adapterRegistry;
    @Mock
    private ProviderCredentialService credentialService;
    @Mock
    private EffectiveAccessService effectiveAccessService;
    @Mock
    private AuditService auditService;
    @Mock
    private SecurityEventService securityEventService;
    @Mock
    private ProviderAdapter vercelAdapter;

    private ProviderIntegrationService integrationService;
    private ObjectMapper objectMapper;
    private UUID workspaceId;
    private UUID callerUserId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        integrationService = new ProviderIntegrationService(
                integrationRepository,
                mappingRepository,
                adapterRegistry,
                credentialService,
                effectiveAccessService,
                auditService,
                securityEventService,
                objectMapper
        );

        workspaceId = UUID.randomUUID();
        callerUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Create Integration: Successful validation sets status ACTIVE and encrypts credentials")
    void testCreateIntegrationSuccess() {
        CreateProviderIntegrationRequest req = new CreateProviderIntegrationRequest(
                ProviderType.VERCEL, "Vercel Prod", "vcel_token_123", Map.of("teamId", "team_1")
        );

        when(integrationRepository.existsByWorkspaceIdAndDisplayName(workspaceId, "Vercel Prod")).thenReturn(false);
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(vercelAdapter.validateConnection(any(), eq("vcel_token_123")))
                .thenReturn(ProviderValidationResult.success("Acme Team", "team_1", Set.of(ProviderCapability.WRITE_SECRETS)));
        doNothing().when(credentialService).encryptAndSetCredentials(any(), eq("vcel_token_123"));
        when(integrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderIntegrationResponse res = integrationService.createIntegration(workspaceId, req, callerUserId);

        assertThat(res).isNotNull();
        assertThat(res.displayName()).isEqualTo("Vercel Prod");
        assertThat(res.status()).isEqualTo(IntegrationStatus.ACTIVE);
        assertThat(res.providerType()).isEqualTo(ProviderType.VERCEL);
        verify(effectiveAccessService).checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);
    }

    @Test
    @DisplayName("Create Integration: Failed validation sets status ERROR without throwing exception")
    void testCreateIntegrationValidationFailure() {
        CreateProviderIntegrationRequest req = new CreateProviderIntegrationRequest(
                ProviderType.VERCEL, "Vercel Staging", "bad_token", Map.of()
        );

        when(integrationRepository.existsByWorkspaceIdAndDisplayName(workspaceId, "Vercel Staging")).thenReturn(false);
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(vercelAdapter.validateConnection(any(), eq("bad_token")))
                .thenReturn(ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Invalid token"));
        doNothing().when(credentialService).encryptAndSetCredentials(any(), eq("bad_token"));
        when(integrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderIntegrationResponse res = integrationService.createIntegration(workspaceId, req, callerUserId);

        assertThat(res.status()).isEqualTo(IntegrationStatus.ERROR);
        assertThat(res.lastErrorCode()).isEqualTo("PROVIDER_AUTHENTICATION_FAILED");
    }

    @Test
    @DisplayName("Atomic Credential Rotation: If new credential fails validation, existing valid credentials are preserved")
    void testAtomicCredentialRotationFailureRollback() {
        UUID intId = UUID.randomUUID();
        ProviderIntegration existing = new ProviderIntegration();
        existing.setId(intId);
        existing.setWorkspaceId(workspaceId);
        existing.setProviderType(ProviderType.VERCEL);
        existing.setDisplayName("Vercel Prod");
        existing.setStatus(IntegrationStatus.ACTIVE);
        existing.setEncryptedCredentialToken("ORIGINAL_ENCRYPTED_DEK");

        when(integrationRepository.findByIdAndWorkspaceId(intId, workspaceId)).thenReturn(Optional.of(existing));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(vercelAdapter.validateConnection(any(), eq("invalid_new_token")))
                .thenReturn(ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Expired token"));

        UpdateProviderIntegrationRequest req = new UpdateProviderIntegrationRequest(
                null, null, "invalid_new_token", null
        );

        assertThatThrownBy(() -> integrationService.updateIntegration(workspaceId, intId, req, callerUserId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("New credential validation failed");

        // Assert original encrypted credential was preserved
        assertThat(existing.getEncryptedCredentialToken()).isEqualTo("ORIGINAL_ENCRYPTED_DEK");
        assertThat(existing.getStatus()).isEqualTo(IntegrationStatus.ACTIVE);
    }

    @Test
    @DisplayName("Validate Integration: Re-validates credentials on demand")
    void testValidateIntegrationOnDemand() {
        UUID intId = UUID.randomUUID();
        ProviderIntegration existing = new ProviderIntegration();
        existing.setId(intId);
        existing.setWorkspaceId(workspaceId);
        existing.setProviderType(ProviderType.VERCEL);
        existing.setStatus(IntegrationStatus.VALIDATING);

        when(integrationRepository.findByIdAndWorkspaceId(intId, workspaceId)).thenReturn(Optional.of(existing));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(existing)).thenReturn("vcel_token");
        when(vercelAdapter.validateConnection(any(), eq("vcel_token")))
                .thenReturn(ProviderValidationResult.success("Team", "id", Set.of(ProviderCapability.VALIDATE_CONNECTION)));
        when(integrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ValidateIntegrationResponse res = integrationService.validateIntegration(workspaceId, intId, callerUserId);

        assertThat(res.valid()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(IntegrationStatus.ACTIVE);
    }

    @Test
    @DisplayName("Delete Integration: Cascades deletion to mappings and integration entity")
    void testDeleteIntegration() {
        UUID intId = UUID.randomUUID();
        ProviderIntegration existing = new ProviderIntegration();
        existing.setId(intId);
        existing.setWorkspaceId(workspaceId);
        existing.setProviderType(ProviderType.VERCEL);
        existing.setDisplayName("To Delete");

        when(integrationRepository.findByIdAndWorkspaceId(intId, workspaceId)).thenReturn(Optional.of(existing));

        integrationService.deleteIntegration(workspaceId, intId, callerUserId);

        verify(mappingRepository).deleteByIntegrationId(intId);
        verify(integrationRepository).delete(existing);
    }
}
