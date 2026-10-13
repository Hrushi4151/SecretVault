package com.secretvault.sync.scheduler;

import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.sync.model.ActualStateResult;
import com.secretvault.sync.model.DesiredSecretState;
import com.secretvault.sync.service.ActualStateResolver;
import com.secretvault.sync.service.DesiredStateResolver;
import com.secretvault.sync.service.DriftDetectionEngine;
import com.secretvault.sync.service.SyncDistributedLockCoordinator;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background scheduler performing bounded periodic drift detection scans across active workspaces.
 * Safely inspects remote provider state without performing any mutations.
 */
@Component
public class SyncDriftScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncDriftScheduler.class);

    private final WorkspaceRepository workspaceRepository;
    private final ProviderIntegrationRepository integrationRepository;
    private final ProviderResourceMappingRepository mappingRepository;
    private final DesiredStateResolver desiredStateResolver;
    private final ActualStateResolver actualStateResolver;
    private final DriftDetectionEngine driftDetectionEngine;

    @Value("${secretvault.sync.scheduler.enabled:true}")
    private boolean schedulerEnabled = true;

    @Value("${secretvault.sync.scheduler.batch-size:50}")
    private int batchSize = 50;

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    private final SyncDistributedLockCoordinator lockCoordinator;

    @org.springframework.beans.factory.annotation.Autowired
    public SyncDriftScheduler(
            WorkspaceRepository workspaceRepository,
            ProviderIntegrationRepository integrationRepository,
            ProviderResourceMappingRepository mappingRepository,
            DesiredStateResolver desiredStateResolver,
            ActualStateResolver actualStateResolver,
            DriftDetectionEngine driftDetectionEngine,
            SyncDistributedLockCoordinator lockCoordinator
    ) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.integrationRepository = Objects.requireNonNull(integrationRepository, "integrationRepository must not be null");
        this.mappingRepository = Objects.requireNonNull(mappingRepository, "mappingRepository must not be null");
        this.desiredStateResolver = Objects.requireNonNull(desiredStateResolver, "desiredStateResolver must not be null");
        this.actualStateResolver = Objects.requireNonNull(actualStateResolver, "actualStateResolver must not be null");
        this.driftDetectionEngine = Objects.requireNonNull(driftDetectionEngine, "driftDetectionEngine must not be null");
        this.lockCoordinator = Objects.requireNonNull(lockCoordinator, "lockCoordinator must not be null");
    }

    public SyncDriftScheduler(
            WorkspaceRepository workspaceRepository,
            ProviderIntegrationRepository integrationRepository,
            ProviderResourceMappingRepository mappingRepository,
            DesiredStateResolver desiredStateResolver,
            ActualStateResolver actualStateResolver,
            DriftDetectionEngine driftDetectionEngine
    ) {
        this(workspaceRepository, integrationRepository, mappingRepository,
                desiredStateResolver, actualStateResolver, driftDetectionEngine,
                new SyncDistributedLockCoordinator(null, true));
    }

    /**
     * Periodic scheduled scan for drift detection. Runs every 5 minutes by default with an initial delay.
     */
    @Scheduled(fixedDelayString = "${secretvault.sync.scheduler.interval-ms:300000}", initialDelay = 60000)
    public void runScheduledDriftScan() {
        if (!schedulerEnabled) {
            log.debug("Sync Drift Scheduler is disabled via configuration");
            return;
        }

        log.info("Starting scheduled background drift detection scan...");
        try {
            List<Workspace> workspaces = workspaceRepository.findAll();
            int processedWorkspaces = 0;

            for (Workspace workspace : workspaces) {
                if (processedWorkspaces >= batchSize) {
                    break;
                }

                UUID workspaceId = workspace.getId();
                String lockToken = UUID.randomUUID().toString();
                boolean acquired;
                try {
                    acquired = lockCoordinator.acquireScanLock(workspaceId, lockToken, java.time.Duration.ofMinutes(5));
                } catch (ApiException e) {
                    log.warn("Drift scan lock acquisition failed closed for workspace [{}]: {}. Skipping workspace to prevent uncoordinated scans.", workspaceId, e.getMessage());
                    continue;
                }

                if (!acquired) {
                    log.debug("Workspace [{}] is already undergoing a drift scan on another instance, skipping", workspaceId);
                    continue;
                }

                try {
                    scanWorkspaceDrift(workspaceId);
                    processedWorkspaces++;
                } catch (Exception e) {
                    log.warn("Scheduled drift scan failed for workspace [{}]: {}", workspaceId, e.getMessage());
                } finally {
                    lockCoordinator.releaseScanLock(workspaceId, lockToken);
                }
            }
            log.info("Scheduled background drift detection finished. Processed [{}] workspaces.", processedWorkspaces);
        } catch (Exception e) {
            log.error("Unhandled error during scheduled drift scan execution", e);
        }
    }

    private void scanWorkspaceDrift(UUID workspaceId) {
        List<ProviderIntegration> activeIntegrations = integrationRepository.findByWorkspaceIdAndStatus(workspaceId, IntegrationStatus.ACTIVE);
        if (activeIntegrations.isEmpty()) {
            return;
        }

        List<ProviderResourceMapping> mappings = mappingRepository.findByWorkspaceId(workspaceId);
        for (ProviderResourceMapping mapping : mappings) {
            try {
                List<DesiredSecretState> desiredStates = desiredStateResolver.resolveDesiredStateForMapping(workspaceId, mapping);
                ActualStateResult actualResult = actualStateResolver.resolveActualState(workspaceId, mapping);
                driftDetectionEngine.detectDriftForMapping(workspaceId, mapping, desiredStates, actualResult, null);
            } catch (Exception e) {
                log.warn("Error scanning drift for mapping [{}] in workspace [{}]: {}", mapping.getId(), workspaceId, e.getMessage());
            }
        }
    }
}
