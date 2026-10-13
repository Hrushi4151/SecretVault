package com.secretvault.incident.worker;

import com.secretvault.incident.entity.IncidentRemediationStatus;
import com.secretvault.incident.entity.SecurityIncidentRemediation;
import com.secretvault.incident.event.IncidentRemediationQueuedEvent;
import com.secretvault.incident.repository.SecurityIncidentRemediationRepository;
import com.secretvault.incident.service.SecurityIncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Autonomous background worker responsible for executing queued incident remediation jobs.
 *
 * Provides two cooperative execution paths:
 * 1. Event-driven immediate execution via {@link TransactionalEventListener} (AFTER_COMMIT)
 * 2. Continuous scheduled polling + stale-claim recovery via {@link Scheduled} (every 2 seconds)
 */
@Component
public class IncidentRemediationWorker {

    private static final Logger log = LoggerFactory.getLogger(IncidentRemediationWorker.class);

    private final SecurityIncidentService incidentService;
    private final SecurityIncidentRemediationRepository remediationRepository;
    private final String workerId;
    private final Duration staleThreshold;
    private final boolean enabled;

    public IncidentRemediationWorker(
            SecurityIncidentService incidentService,
            SecurityIncidentRemediationRepository remediationRepository,
            @Value("${secretvault.incident.worker.id:#{null}}") String workerId,
            @Value("${secretvault.incident.worker.stale-threshold-minutes:5}") int staleThresholdMinutes,
            @Value("${secretvault.incident.worker.enabled:true}") boolean enabled
    ) {
        this.incidentService = incidentService;
        this.remediationRepository = remediationRepository;
        this.workerId = (workerId != null && !workerId.isBlank())
                ? workerId
                : "remediation-worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.staleThreshold = Duration.ofMinutes(staleThresholdMinutes);
        this.enabled = enabled;
    }

    public String getWorkerId() {
        return workerId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Immediate async trigger after database transaction commits the QUEUED remediation.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void onRemediationQueued(IncidentRemediationQueuedEvent event) {
        if (!enabled || event == null || event.executionId() == null) {
            return;
        }
        try {
            log.info("[{}] Event-driven remediation claim triggered for task [{}]", workerId, event.executionId());
            incidentService.executeRemediationTask(event.executionId(), workerId);
        } catch (Exception e) {
            log.error("[{}] Error executing queued remediation task [{}]: {}", workerId, event.executionId(), e.getMessage(), e);
        }
    }

    /**
     * Periodic scheduled poller:
     * 1. Recovers stale worker leases stuck in CLAIMED past threshold.
     * 2. Claims and executes backlog or unhandled QUEUED tasks FIFO.
     */
    @Scheduled(fixedDelayString = "${secretvault.incident.worker.poll-interval-ms:2000}", initialDelay = 1000)
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        processRemediationBatch();
    }

    /**
     * Executes one complete worker cycle: recovers stale claims and dispatches queued tasks.
     * Can also be called synchronously in integration tests.
     *
     * @return count of tasks processed to terminal status in this cycle
     */
    public int processRemediationBatch() {
        int processedCount = 0;
        try {
            // 1. Stale-claim recovery (worker crash, restart, network disconnect)
            int recovered = incidentService.recoverStaleRemediations(staleThreshold);
            if (recovered > 0) {
                log.warn("[{}] Stale claim recovery reclaimed {} tasks", workerId, recovered);
            }

            // 2. Poll queued jobs (ordered by creation time FIFO)
            List<SecurityIncidentRemediation> queued = remediationRepository.findByStatusOrderByCreatedAtAsc(
                    IncidentRemediationStatus.QUEUED,
                    PageRequest.of(0, 10)
            );

            for (SecurityIncidentRemediation rem : queued) {
                try {
                    var response = incidentService.executeRemediationTask(rem.getId(), workerId);
                    if (response != null && (response.status() == IncidentRemediationStatus.COMPLETED
                            || response.status() == IncidentRemediationStatus.PARTIAL_FAILURE
                            || response.status() == IncidentRemediationStatus.FAILED)) {
                        processedCount++;
                    }
                } catch (Exception e) {
                    log.error("[{}] Error executing remediation task [{}]: {}", workerId, rem.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception ex) {
            log.error("[{}] Error in remediation worker cycle: {}", workerId, ex.getMessage(), ex);
        }
        return processedCount;
    }
}
