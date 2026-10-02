package com.secretvault.sync.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.service.SecurityFindingService;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.DriftRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Core engine for evaluating state divergence between SecretVault desired state
 * and external provider actual state.
 * Generates deterministic, deduplicated drift records and seamlessly integrates with
 * Security Intelligence telemetry.
 */
@Service
public class DriftDetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DriftDetectionEngine.class);

    private final DriftRecordRepository driftRecordRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecurityEventService securityEventService;
    private final SecurityFindingService securityFindingService;
    private final AuditService auditService;

    public DriftDetectionEngine(
            DriftRecordRepository driftRecordRepository,
            EnvironmentRepository environmentRepository,
            SecurityEventService securityEventService,
            SecurityFindingService securityFindingService,
            AuditService auditService
    ) {
        this.driftRecordRepository = Objects.requireNonNull(driftRecordRepository, "driftRecordRepository must not be null");
        this.environmentRepository = Objects.requireNonNull(environmentRepository, "environmentRepository must not be null");
        this.securityEventService = Objects.requireNonNull(securityEventService, "securityEventService must not be null");
        this.securityFindingService = Objects.requireNonNull(securityFindingService, "securityFindingService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    /**
     * Executes drift detection for a single provider resource mapping.
     */
    @Transactional
    public List<DriftRecord> detectDriftForMapping(
            UUID workspaceId,
            ProviderResourceMapping mapping,
            List<DesiredSecretState> desiredStates,
            ActualStateResult actualResult,
            UUID actorUserId
    ) {
        if (mapping == null || !mapping.getWorkspaceId().equals(workspaceId)) {
            return Collections.emptyList();
        }

        EnvType envType = getEnvType(mapping.getEnvironmentId());
        Instant now = Instant.now();
        List<DriftRecord> detectedDrifts = new ArrayList<>();
        Set<String> activeDetectedFingerprints = new HashSet<>();

        // Handle provider error states (unavailability, auth, unsupported)
        if (actualResult.isError()) {
            DriftType driftType = actualResult.errorDriftType();
            DriftSeverity severity = calculateSeverity(envType, driftType);
            String targetId = "MAPPING:" + mapping.getId();
            String fingerprint = DriftFingerprintUtil.computeFingerprint(
                    workspaceId, mapping.getIntegrationId(), mapping.getId(), targetId, driftType
            );
            activeDetectedFingerprints.add(fingerprint);

            DriftRecord record = upsertDriftRecord(
                    workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                    mapping.getIntegrationId(), mapping.getId(),
                    null, "PROVIDER_CONNECTION", targetId,
                    driftType, severity, null, null, fingerprint,
                    actualResult.errorCode(), actualResult.errorMessage(), now
            );
            detectedDrifts.add(record);

            // Record security telemetry
            SecurityEventType eventType = driftType == DriftType.PROVIDER_UNAVAILABLE ?
                    SecurityEventType.PROVIDER_UNAVAILABLE : SecurityEventType.DRIFT_DETECTED;

            securityEventService.recordEvent(
                    workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                    actorUserId, eventType,
                    mapEventSeverity(severity), SecurityEventOutcome.FAILURE,
                    "SYNC_ENGINE", null, null, null,
                    Map.of(
                            "mappingId", mapping.getId().toString(),
                            "integrationId", mapping.getIntegrationId().toString(),
                            "driftType", driftType.name(),
                            "errorCode", actualResult.errorCode() != null ? actualResult.errorCode() : "UNKNOWN"
                    )
            );

            return detectedDrifts;
        }

        // Provider is accessible: compare desired vs actual secrets
        Map<String, DesiredSecretState> desiredMap = new LinkedHashMap<>();
        if (desiredStates != null) {
            for (DesiredSecretState desired : desiredStates) {
                desiredMap.put(desired.secretName(), desired);
            }
        }

        Map<String, ProviderSecretState> actualMap = new LinkedHashMap<>();
        if (actualResult.states() != null) {
            for (ProviderSecretState actual : actualResult.states()) {
                actualMap.put(actual.providerSecretName(), actual);
            }
        }

        // 1. Identify MISSING_FROM_PROVIDER
        for (DesiredSecretState desired : desiredMap.values()) {
            if (!actualMap.containsKey(desired.secretName())) {
                DriftType driftType = DriftType.MISSING_FROM_PROVIDER;
                DriftSeverity severity = calculateSeverity(envType, driftType);
                String fingerprint = DriftFingerprintUtil.computeFingerprint(
                        workspaceId, mapping.getIntegrationId(), mapping.getId(), desired.secretName(), driftType
                );
                activeDetectedFingerprints.add(fingerprint);

                DriftRecord record = upsertDriftRecord(
                        workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                        mapping.getIntegrationId(), mapping.getId(),
                        desired.secretId(), desired.secretName(), null,
                        driftType, severity, desired.desiredFingerprint(), null, fingerprint,
                        null, "Secret exists in SecretVault but is absent on provider", now
                );
                detectedDrifts.add(record);
            }
        }

        // 2. Identify EXTRA_IN_PROVIDER
        for (ProviderSecretState actual : actualMap.values()) {
            if (!desiredMap.containsKey(actual.providerSecretName())) {
                DriftType driftType = DriftType.EXTRA_IN_PROVIDER;
                DriftSeverity severity = calculateSeverity(envType, driftType);
                String fingerprint = DriftFingerprintUtil.computeFingerprint(
                        workspaceId, mapping.getIntegrationId(), mapping.getId(), actual.providerSecretName(), driftType
                );
                activeDetectedFingerprints.add(fingerprint);

                DriftRecord record = upsertDriftRecord(
                        workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                        mapping.getIntegrationId(), mapping.getId(),
                        null, actual.providerSecretName(), actual.providerSecretIdentifier(),
                        driftType, severity, null, actual.providerFingerprint(), fingerprint,
                        null, "Secret exists on external provider but is unmanaged in SecretVault", now
                );
                detectedDrifts.add(record);
            }
        }

        // 3. Resolve previously OPEN or ACKNOWLEDGED drifts that are no longer detected
        List<DriftRecord> existingOpen = driftRecordRepository.findByMappingIdAndStatus(mapping.getId(), DriftStatus.OPEN);
        List<DriftRecord> existingAck = driftRecordRepository.findByMappingIdAndStatus(mapping.getId(), DriftStatus.ACKNOWLEDGED);
        List<DriftRecord> existingToEvaluate = new ArrayList<>(existingOpen);
        existingToEvaluate.addAll(existingAck);

        for (DriftRecord existing : existingToEvaluate) {
            if (!activeDetectedFingerprints.contains(existing.getFingerprint())) {
                existing.setStatus(DriftStatus.RESOLVED);
                existing.setResolvedAt(now);
                existing.setResolvedBy(actorUserId);
                existing.setResolutionReason("Resolved: drift no longer present on provider");
                existing.setUpdatedAt(now);
                driftRecordRepository.save(existing);

                // Telemetry for resolved drift
                securityEventService.recordEvent(
                        workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                        actorUserId, SecurityEventType.DRIFT_RESOLVED,
                        SecurityEventSeverity.LOW, SecurityEventOutcome.SUCCESS,
                        "SYNC_ENGINE", null, null, null,
                        Map.of(
                                "mappingId", mapping.getId().toString(),
                                "driftType", existing.getDriftType().name(),
                                "secretName", existing.getSecretName()
                        )
                );

                auditService.recordAudit(
                        null, workspaceId, actorUserId, "SYSTEM",
                        AuditAction.DRIFT_RESOLVED, "DRIFT_RECORD",
                        existing.getId(), null, null, "SUCCESS"
                );
            }
        }

        // Emit telemetry & audit for detected drifts
        for (DriftRecord detected : detectedDrifts) {
            securityEventService.recordEvent(
                    workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                    actorUserId, SecurityEventType.DRIFT_DETECTED,
                    mapEventSeverity(detected.getSeverity()), SecurityEventOutcome.FAILURE,
                    "SYNC_ENGINE", null, null, null,
                    Map.of(
                            "mappingId", mapping.getId().toString(),
                            "driftType", detected.getDriftType().name(),
                            "severity", detected.getSeverity().name(),
                            "secretName", detected.getSecretName()
                    )
            );

            auditService.recordAudit(
                    null, workspaceId, actorUserId, "SYSTEM",
                    AuditAction.DRIFT_DETECTED, "DRIFT_RECORD",
                    detected.getId(), null, null, "SUCCESS"
            );

            // If CRITICAL/HIGH drift in PRODUCTION environment, create a SecurityFinding
            if (envType == EnvType.PRODUCTION && (detected.getSeverity() == DriftSeverity.CRITICAL || detected.getSeverity() == DriftSeverity.HIGH)) {
                createSecurityFindingIfApplicable(workspaceId, mapping, detected);
            }
        }

        return detectedDrifts;
    }

    private DriftRecord upsertDriftRecord(
            UUID workspaceId, UUID projectId, UUID environmentId,
            UUID integrationId, UUID mappingId,
            UUID secretId, String secretName, String providerSecretIdentifier,
            DriftType driftType, DriftSeverity severity,
            String desiredFingerprint, String observedFingerprint,
            String fingerprint, String errorCode, String details, Instant now
    ) {
        Optional<DriftRecord> existingOpt = driftRecordRepository.findByWorkspaceIdAndFingerprint(workspaceId, fingerprint);
        if (existingOpt.isPresent()) {
            DriftRecord existing = existingOpt.get();
            existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
            existing.setLastDetectedAt(now);
            existing.setSeverity(severity);
            existing.setDesiredFingerprint(desiredFingerprint);
            existing.setObservedFingerprint(observedFingerprint);
            existing.setErrorCode(errorCode);
            existing.setDetailsJson(details);
            existing.setUpdatedAt(now);
            if (existing.getStatus() == DriftStatus.RESOLVED) {
                existing.setStatus(DriftStatus.OPEN);
                existing.setResolvedAt(null);
                existing.setResolvedBy(null);
                existing.setResolutionReason(null);
            }
            return driftRecordRepository.save(existing);
        }

        DriftRecord newRecord = new DriftRecord(
                workspaceId, projectId, environmentId, integrationId, mappingId,
                secretId, secretName, providerSecretIdentifier, driftType, severity,
                desiredFingerprint, observedFingerprint, fingerprint, errorCode, details
        );
        newRecord.setFirstDetectedAt(now);
        newRecord.setLastDetectedAt(now);
        newRecord.setStatus(DriftStatus.OPEN);
        return driftRecordRepository.save(newRecord);
    }

    private void createSecurityFindingIfApplicable(UUID workspaceId, ProviderResourceMapping mapping, DriftRecord drift) {
        try {
            FindingSeverity findingSeverity = drift.getSeverity() == DriftSeverity.CRITICAL ?
                    FindingSeverity.CRITICAL : FindingSeverity.HIGH;

            SecurityFindingDraft draft = new SecurityFindingDraft(
                    workspaceId,
                    mapping.getProjectId(),
                    mapping.getEnvironmentId(),
                    FindingCategory.PROVIDER_DRIFT_DETECTED,
                    findingSeverity,
                    FindingConfidence.HIGH,
                    "Provider Secret Drift in Production: " + drift.getSecretName(),
                    "External platform provider state has drifted for secret [" + drift.getSecretName() + "] (" + drift.getDriftType() + ")",
                    "Execute dry-run and synchronize secret state to external provider using SecretVault Sync Engine.",
                    "DRIFT:" + drift.getFingerprint(),
                    Map.of(
                            "mappingId", mapping.getId().toString(),
                            "driftType", drift.getDriftType().name(),
                            "secretName", drift.getSecretName()
                    )
            );
            securityFindingService.upsertFinding(draft);
        } catch (Exception e) {
            log.warn("Failed to create security finding for drift [{}]: {}", drift.getId(), e.getMessage());
        }
    }

    public static DriftSeverity calculateSeverity(EnvType envType, DriftType driftType) {
        if (envType == null) envType = EnvType.DEVELOPMENT;

        return switch (envType) {
            case PRODUCTION -> switch (driftType) {
                case VALUE_MISMATCH, MISSING_FROM_PROVIDER -> DriftSeverity.CRITICAL;
                case EXTRA_IN_PROVIDER, PERMISSION_DENIED -> DriftSeverity.HIGH;
                case PROVIDER_UNAVAILABLE, RESOURCE_MAPPING_MISMATCH -> DriftSeverity.HIGH;
                default -> DriftSeverity.MEDIUM;
            };
            case STAGING -> switch (driftType) {
                case VALUE_MISMATCH, MISSING_FROM_PROVIDER -> DriftSeverity.HIGH;
                case EXTRA_IN_PROVIDER, PERMISSION_DENIED, PROVIDER_UNAVAILABLE -> DriftSeverity.MEDIUM;
                default -> DriftSeverity.LOW;
            };
            case DEVELOPMENT -> switch (driftType) {
                case PERMISSION_DENIED, PROVIDER_UNAVAILABLE -> DriftSeverity.MEDIUM;
                default -> DriftSeverity.LOW;
            };
        };
    }

    private EnvType getEnvType(UUID environmentId) {
        if (environmentId == null) return EnvType.DEVELOPMENT;
        return environmentRepository.findById(environmentId)
                .map(Environment::getEnvType)
                .orElse(EnvType.DEVELOPMENT);
    }

    private SecurityEventSeverity mapEventSeverity(DriftSeverity severity) {
        if (severity == null) return SecurityEventSeverity.INFO;
        return switch (severity) {
            case CRITICAL -> SecurityEventSeverity.CRITICAL;
            case HIGH -> SecurityEventSeverity.HIGH;
            case MEDIUM -> SecurityEventSeverity.MEDIUM;
            case LOW -> SecurityEventSeverity.LOW;
            case INFO -> SecurityEventSeverity.INFO;
        };
    }
}
