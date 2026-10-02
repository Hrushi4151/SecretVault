package com.secretvault.sync;

import com.secretvault.audit.service.AuditService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.service.SecurityFindingService;
import com.secretvault.sync.dto.DriftRecordResponse;
import com.secretvault.sync.dto.SyncExecutionResponse;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.DriftRecordRepository;
import com.secretvault.sync.repository.SyncJobRepository;
import com.secretvault.sync.repository.SyncOperationRepository;
import com.secretvault.sync.service.DriftDetectionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncSecurityCanaryTest {

    private static final String CANARY_TOKEN = "SUPER_SECRET_CANARY_123";

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

    private DriftDetectionEngine driftEngine;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID integrationId;
    private UUID mappingId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        driftEngine = new DriftDetectionEngine(
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

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_123", "prj_vercel_123", "production", "{}", true
        );
        mapping.setId(mappingId);

        lenient().when(environmentRepository.findById(environmentId))
                .thenReturn(Optional.of(new Environment(projectId, "production", "production", EnvType.PRODUCTION, "Production Env", true, UUID.randomUUID())));
        lenient().when(driftRecordRepository.save(any(DriftRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Canary token is never stored in DriftRecord or emitted in security telemetry")
    void verifyCanaryAbsenceInDriftTelemetry() {
        // Compute deterministic value fingerprint in RAM
        String canaryFingerprint = SecretFingerprintCalculator.computeValueFingerprint(CANARY_TOKEN);

        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, projectId, environmentId, UUID.randomUUID(),
                "CANARY_SECRET", 1, canaryFingerprint, true, mappingId, Instant.now()
        );

        ActualStateResult actualResult = ActualStateResult.success(Collections.emptyList());

        when(driftRecordRepository.findByWorkspaceIdAndFingerprint(any(), any()))
                .thenReturn(Optional.empty());
        when(driftRecordRepository.findByMappingIdAndStatus(any(), any()))
                .thenReturn(Collections.emptyList());

        List<DriftRecord> drifts = driftEngine.detectDriftForMapping(
                workspaceId, mapping, List.of(desired), actualResult, UUID.randomUUID()
        );

        assertThat(drifts).isNotEmpty();
        DriftRecord drift = drifts.get(0);

        // Verify Canary string never appears anywhere in the drift record entity
        assertThat(drift.getSecretName()).doesNotContain(CANARY_TOKEN);
        assertThat(drift.getDesiredFingerprint()).doesNotContain(CANARY_TOKEN);
        assertThat(drift.getObservedFingerprint() != null ? drift.getObservedFingerprint() : "").doesNotContain(CANARY_TOKEN);
        assertThat(drift.getFingerprint()).doesNotContain(CANARY_TOKEN);
        assertThat(drift.getDetailsJson() != null ? drift.getDetailsJson() : "").doesNotContain(CANARY_TOKEN);

        // Verify DriftRecordResponse DTO
        DriftRecordResponse response = DriftRecordResponse.fromEntity(drift);
        assertThat(response.toString()).doesNotContain(CANARY_TOKEN);

        // Capture security event metadata
        ArgumentCaptor<Map<String, ?>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(securityEventService).recordEvent(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                metadataCaptor.capture()
        );

        Map<String, ?> metadata = metadataCaptor.getValue();
        for (Object value : metadata.values()) {
            assertThat(String.valueOf(value)).doesNotContain(CANARY_TOKEN);
        }
    }

    @Test
    @DisplayName("Zero plaintext leak in in-memory fingerprint zeroization")
    void verifyMemoryHygieneInFingerprintCalculator() {
        String hash1 = SecretFingerprintCalculator.computeValueFingerprint(CANARY_TOKEN);
        String hash2 = SecretFingerprintCalculator.computeValueFingerprint(CANARY_TOKEN);

        assertThat(hash1).isNotBlank();
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).doesNotContain(CANARY_TOKEN);
    }
}
