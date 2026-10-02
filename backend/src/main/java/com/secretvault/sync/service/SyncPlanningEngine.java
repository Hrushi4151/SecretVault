package com.secretvault.sync.service;

import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Builds a deterministic, safe synchronization plan before executing any provider mutations.
 * Enforces conservative deletion, policy boundaries, and explicit operation reasons.
 */
@Service
public class SyncPlanningEngine {

    private static final Logger log = LoggerFactory.getLogger(SyncPlanningEngine.class);

    /**
     * Constructs a comprehensive SyncPlan for the target scope and policies.
     */
    public SyncPlan planSync(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId,
            ReconciliationPolicy policy,
            Map<ProviderResourceMapping, List<DesiredSecretState>> desiredMap,
            Map<UUID, ActualStateResult> actualMap,
            List<DriftRecord> driftRecords
    ) {
        ReconciliationPolicy effectivePolicy = policy != null ? policy : ReconciliationPolicy.SAFE_RECONCILIATION;
        List<SyncOperationPlan> plannedOperations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (desiredMap == null || desiredMap.isEmpty()) {
            return new SyncPlan(workspaceId, scope, scopeResourceId, effectivePolicy, Collections.emptyList(), driftRecords, warnings);
        }

        for (Map.Entry<ProviderResourceMapping, List<DesiredSecretState>> entry : desiredMap.entrySet()) {
            ProviderResourceMapping mapping = entry.getKey();
            List<DesiredSecretState> desiredStates = entry.getValue() != null ? entry.getValue() : Collections.emptyList();
            ActualStateResult actualResult = actualMap != null ? actualMap.get(mapping.getId()) : null;

            if (actualResult == null || actualResult.isError()) {
                String errorMsg = actualResult != null ? actualResult.errorMessage() : "Provider state unavailable";
                String errCode = actualResult != null ? actualResult.errorCode() : "PROVIDER_ERROR";
                warnings.add("Cannot plan sync for mapping [" + mapping.getId() + "]: " + errorMsg);

                plannedOperations.add(new SyncOperationPlan(
                        null,
                        "MAPPING:" + mapping.getId(),
                        mapping.getId(),
                        mapping.getIntegrationId(),
                        SyncOperationType.ERROR,
                        SyncOperationStatus.BLOCKED,
                        null,
                        null,
                        "Blocked due to provider error: " + errorMsg,
                        errCode,
                        errorMsg
                ));
                continue;
            }

            Map<String, DesiredSecretState> desiredByName = new LinkedHashMap<>();
            for (DesiredSecretState d : desiredStates) {
                desiredByName.put(d.secretName(), d);
            }

            Map<String, ProviderSecretState> actualByName = new LinkedHashMap<>();
            for (ProviderSecretState a : actualResult.states()) {
                actualByName.put(a.providerSecretName(), a);
            }

            // 1. Process all Desired Secrets
            for (DesiredSecretState desired : desiredStates) {
                ProviderSecretState actual = actualByName.get(desired.secretName());

                if (actual == null) {
                    // Secret is missing from provider -> CREATE
                    if (effectivePolicy == ReconciliationPolicy.DETECT_ONLY) {
                        plannedOperations.add(new SyncOperationPlan(
                                desired.secretId(),
                                desired.secretName(),
                                mapping.getId(),
                                mapping.getIntegrationId(),
                                SyncOperationType.CREATE,
                                SyncOperationStatus.BLOCKED,
                                desired.desiredFingerprint(),
                                null,
                                "Policy DETECT_ONLY blocks mutation",
                                null,
                                null
                        ));
                    } else {
                        plannedOperations.add(new SyncOperationPlan(
                                desired.secretId(),
                                desired.secretName(),
                                mapping.getId(),
                                mapping.getIntegrationId(),
                                SyncOperationType.CREATE,
                                SyncOperationStatus.PENDING,
                                desired.desiredFingerprint(),
                                null,
                                "Secret is missing on provider, pushing desired state",
                                null,
                                null
                        ));
                    }
                } else {
                    // Secret exists on both provider and SecretVault -> check if update or no-op
                    // In current Phase 7/8 model, if both exist, we treat as in-sync / idempotent NO_OP or UPDATE
                    // In safe reconciliation, re-pushing desired state ensures provider has latest value
                    if (effectivePolicy == ReconciliationPolicy.DETECT_ONLY) {
                        plannedOperations.add(new SyncOperationPlan(
                                desired.secretId(),
                                desired.secretName(),
                                mapping.getId(),
                                mapping.getIntegrationId(),
                                SyncOperationType.NO_OP,
                                SyncOperationStatus.SKIPPED,
                                desired.desiredFingerprint(),
                                actual.providerFingerprint(),
                                "Secret already exists on provider; DETECT_ONLY policy",
                                null,
                                null
                        ));
                    } else {
                        // Secret exists on both -> UPDATE/NO_OP (idempotent push)
                        plannedOperations.add(new SyncOperationPlan(
                                desired.secretId(),
                                desired.secretName(),
                                mapping.getId(),
                                mapping.getIntegrationId(),
                                SyncOperationType.UPDATE,
                                SyncOperationStatus.PENDING,
                                desired.desiredFingerprint(),
                                actual.providerFingerprint(),
                                "Secret exists on provider, applying latest desired version",
                                null,
                                null
                        ));
                    }
                }
            }

            // 2. Process Extra Remote Secrets on Provider (Conservative Deletion Policy)
            for (ProviderSecretState actual : actualResult.states()) {
                if (!desiredByName.containsKey(actual.providerSecretName())) {
                    warnings.add("Remote secret [" + actual.providerSecretName() + "] is unmanaged in SecretVault and will not be automatically deleted.");
                    plannedOperations.add(new SyncOperationPlan(
                            null,
                            actual.providerSecretName(),
                            mapping.getId(),
                            mapping.getIntegrationId(),
                            SyncOperationType.BLOCKED,
                            SyncOperationStatus.BLOCKED,
                            null,
                            actual.providerFingerprint(),
                            "Conservative deletion: unmanaged remote secrets are never deleted automatically",
                            null,
                            null
                    ));
                }
            }
        }

        return new SyncPlan(workspaceId, scope, scopeResourceId, effectivePolicy, plannedOperations, driftRecords, warnings);
    }
}
