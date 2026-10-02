package com.secretvault.sync;

import com.secretvault.audit.service.AuditService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.service.SecurityFindingService;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.DriftRecordRepository;
import com.secretvault.sync.service.DriftDetectionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriftDetectionEngineTest {

    @Mock
    private DriftRecordRepository driftRecordRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SecurityEventService securityEventService;

    @Mock
    private SecurityFindingService securityFindingService;

    @Mock
    private AuditService auditService;

    private DriftDetectionEngine engine;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID integrationId;
    private UUID mappingId;
    private UUID actorUserId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        engine = new DriftDetectionEngine(
                driftRecordRepository,
                environmentRepository,
                securityEventService,
                securityFindingService,
                auditService
        );

        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        mappingId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_123", "prj_vercel_123", "production", "{}", true
        );
        mapping.setId(mappingId);

        Environment env = new Environment(projectId, "production", "production", EnvType.PRODUCTION, "Production Env", true, UUID.randomUUID());
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(env));

        when(driftRecordRepository.save(any(DriftRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Detects MISSING_FROM_PROVIDER when secret exists in SecretVault but absent on provider")
    void detectMissingSecret() {
        UUID secretId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, projectId, environmentId, secretId,
                "DATABASE_URL", 1, "fp_desired_123", true, mappingId, Instant.now()
        );

        ActualStateResult actualResult = ActualStateResult.success(Collections.emptyList());

        when(driftRecordRepository.findByWorkspaceIdAndFingerprint(eq(workspaceId), any()))
                .thenReturn(Optional.empty());
        when(driftRecordRepository.findByMappingIdAndStatus(eq(mappingId), eq(DriftStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(driftRecordRepository.findByMappingIdAndStatus(eq(mappingId), eq(DriftStatus.ACKNOWLEDGED)))
                .thenReturn(Collections.emptyList());

        List<DriftRecord> drifts = engine.detectDriftForMapping(
                workspaceId, mapping, List.of(desired), actualResult, actorUserId
        );

        assertThat(drifts).hasSize(1);
        DriftRecord drift = drifts.get(0);
        assertThat(drift.getDriftType()).isEqualTo(DriftType.MISSING_FROM_PROVIDER);
        assertThat(drift.getSecretName()).isEqualTo("DATABASE_URL");
        assertThat(drift.getSeverity()).isEqualTo(DriftSeverity.CRITICAL); // Production missing secret
        assertThat(drift.getStatus()).isEqualTo(DriftStatus.OPEN);
        assertThat(drift.getOccurrenceCount()).isEqualTo(1);

        verify(securityFindingService).upsertFinding(any());
    }

    @Test
    @DisplayName("Detects EXTRA_IN_PROVIDER when secret exists on provider but is unmanaged in SecretVault")
    void detectExtraSecret() {
        ProviderSecretState providerSecret = new ProviderSecretState(
                integrationId, mappingId, "prj_vercel_123", "production",
                "UNMANAGED_TOKEN", "env_abc_999", null, true, "production", Instant.now()
        );

        ActualStateResult actualResult = ActualStateResult.success(List.of(providerSecret));

        when(driftRecordRepository.findByWorkspaceIdAndFingerprint(eq(workspaceId), any()))
                .thenReturn(Optional.empty());
        when(driftRecordRepository.findByMappingIdAndStatus(eq(mappingId), eq(DriftStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(driftRecordRepository.findByMappingIdAndStatus(eq(mappingId), eq(DriftStatus.ACKNOWLEDGED)))
                .thenReturn(Collections.emptyList());

        List<DriftRecord> drifts = engine.detectDriftForMapping(
                workspaceId, mapping, Collections.emptyList(), actualResult, actorUserId
        );

        assertThat(drifts).hasSize(1);
        DriftRecord drift = drifts.get(0);
        assertThat(drift.getDriftType()).isEqualTo(DriftType.EXTRA_IN_PROVIDER);
        assertThat(drift.getSecretName()).isEqualTo("UNMANAGED_TOKEN");
        assertThat(drift.getSeverity()).isEqualTo(DriftSeverity.HIGH);
    }

    @Test
    @DisplayName("Distinguishes PROVIDER_UNAVAILABLE from missing secret on remote error")
    void detectProviderUnavailable() {
        ActualStateResult actualResult = ActualStateResult.error(
                DriftType.PROVIDER_UNAVAILABLE, "PROVIDER_TIMEOUT", "Connection timed out"
        );

        when(driftRecordRepository.findByWorkspaceIdAndFingerprint(eq(workspaceId), any()))
                .thenReturn(Optional.empty());

        List<DriftRecord> drifts = engine.detectDriftForMapping(
                workspaceId, mapping, Collections.emptyList(), actualResult, actorUserId
        );

        assertThat(drifts).hasSize(1);
        DriftRecord drift = drifts.get(0);
        assertThat(drift.getDriftType()).isEqualTo(DriftType.PROVIDER_UNAVAILABLE);
        assertThat(drift.getErrorCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(drift.getSeverity()).isEqualTo(DriftSeverity.HIGH);
    }

    @Test
    @DisplayName("Distinguishes PERMISSION_DENIED on provider credential failure")
    void detectPermissionDenied() {
        ActualStateResult actualResult = ActualStateResult.error(
                DriftType.PERMISSION_DENIED, "PROVIDER_AUTHENTICATION_FAILED", "Invalid API token"
        );

        when(driftRecordRepository.findByWorkspaceIdAndFingerprint(eq(workspaceId), any()))
                .thenReturn(Optional.empty());

        List<DriftRecord> drifts = engine.detectDriftForMapping(
                workspaceId, mapping, Collections.emptyList(), actualResult, actorUserId
        );

        assertThat(drifts).hasSize(1);
        DriftRecord drift = drifts.get(0);
        assertThat(drift.getDriftType()).isEqualTo(DriftType.PERMISSION_DENIED);
        assertThat(drift.getErrorCode()).isEqualTo("PROVIDER_AUTHENTICATION_FAILED");
    }

    @Test
    @DisplayName("Repeated drift detection increments occurrence count and preserves deduplicated fingerprint")
    void deduplicateRepeatedDetection() {
        UUID secretId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, projectId, environmentId, secretId,
                "API_KEY", 1, "fp_1", true, mappingId, Instant.now()
        );

        ActualStateResult actualResult = ActualStateResult.success(Collections.emptyList());

        String fp = DriftFingerprintUtil.computeFingerprint(
                workspaceId, integrationId, mappingId, "API_KEY", DriftType.MISSING_FROM_PROVIDER
        );

        DriftRecord existingRecord = new DriftRecord(
                workspaceId, projectId, environmentId, integrationId, mappingId,
                secretId, "API_KEY", null, DriftType.MISSING_FROM_PROVIDER,
                DriftSeverity.CRITICAL, "fp_1", null, fp
        );
        existingRecord.setOccurrenceCount(3);

        when(driftRecordRepository.findByWorkspaceIdAndFingerprint(workspaceId, fp))
                .thenReturn(Optional.of(existingRecord));
        when(driftRecordRepository.findByMappingIdAndStatus(eq(mappingId), eq(DriftStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(driftRecordRepository.findByMappingIdAndStatus(eq(mappingId), eq(DriftStatus.ACKNOWLEDGED)))
                .thenReturn(Collections.emptyList());

        List<DriftRecord> drifts = engine.detectDriftForMapping(
                workspaceId, mapping, List.of(desired), actualResult, actorUserId
        );

        assertThat(drifts).hasSize(1);
        assertThat(existingRecord.getOccurrenceCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("Automatically transitions previously OPEN drift to RESOLVED when drift is corrected")
    void autoResolveCorrectedDrift() {
        UUID secretId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, projectId, environmentId, secretId,
                "STRIPE_KEY", 1, "fp_stripe", true, mappingId, Instant.now()
        );

        ProviderSecretState providerSecret = new ProviderSecretState(
                integrationId, mappingId, "prj_vercel_123", "production",
                "STRIPE_KEY", "env_stripe_1", null, true, "production", Instant.now()
        );

        ActualStateResult actualResult = ActualStateResult.success(List.of(providerSecret));

        // Existing drift record for STRIPE_KEY being missing
        String oldFp = DriftFingerprintUtil.computeFingerprint(
                workspaceId, integrationId, mappingId, "STRIPE_KEY", DriftType.MISSING_FROM_PROVIDER
        );
        DriftRecord oldDrift = new DriftRecord(
                workspaceId, projectId, environmentId, integrationId, mappingId,
                secretId, "STRIPE_KEY", null, DriftType.MISSING_FROM_PROVIDER,
                DriftSeverity.CRITICAL, "fp_stripe", null, oldFp
        );
        oldDrift.setStatus(DriftStatus.OPEN);

        when(driftRecordRepository.findByMappingIdAndStatus(mappingId, DriftStatus.OPEN))
                .thenReturn(List.of(oldDrift));
        when(driftRecordRepository.findByMappingIdAndStatus(mappingId, DriftStatus.ACKNOWLEDGED))
                .thenReturn(Collections.emptyList());

        List<DriftRecord> activeDrifts = engine.detectDriftForMapping(
                workspaceId, mapping, List.of(desired), actualResult, actorUserId
        );

        assertThat(activeDrifts).isEmpty();
        assertThat(oldDrift.getStatus()).isEqualTo(DriftStatus.RESOLVED);
        assertThat(oldDrift.getResolvedAt()).isNotNull();
        assertThat(oldDrift.getResolvedBy()).isEqualTo(actorUserId);
    }
}
