package com.secretvault.health.service;

import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.service.SecurityIncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SecretAnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(SecretAnomalyDetectionService.class);

    private final EventPublisher eventPublisher;
    private final SecurityIncidentService incidentService;

    // Sliding window counter for reveals & failures per aggregate
    private final Map<String, AtomicInteger> revealCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();

    private static final int REVEAL_ANOMALY_THRESHOLD = 20; // >20 reveals in window
    private static final int FAILURE_ANOMALY_THRESHOLD = 10; // >10 failures in window

    public SecretAnomalyDetectionService(
            EventPublisher eventPublisher,
            SecurityIncidentService incidentService
    ) {
        this.eventPublisher = eventPublisher;
        this.incidentService = incidentService;
    }

    public void trackReveal(UUID workspaceId, UUID secretId, UUID actorId) {
        if (workspaceId == null || secretId == null) return;
        String key = workspaceId + ":" + secretId;
        int count = revealCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (count >= REVEAL_ANOMALY_THRESHOLD) {
            triggerAnomaly(workspaceId, "SECRET", secretId.toString(), "Unusual Secret Reveal Frequency (" + count + " reveals)", actorId);
            revealCounters.get(key).set(0); // reset window
        }
    }

    public void trackFailedAccess(UUID workspaceId, String aggregateType, String aggregateId, UUID actorId) {
        if (workspaceId == null) return;
        String key = workspaceId + ":" + aggregateType + ":" + aggregateId;
        int count = failureCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (count >= FAILURE_ANOMALY_THRESHOLD) {
            triggerAnomaly(workspaceId, aggregateType, aggregateId, "High Volume of Failed Access Denials (" + count + " denials)", actorId);
            failureCounters.get(key).set(0); // reset window
        }
    }

    private void triggerAnomaly(UUID workspaceId, String aggregateType, String aggregateId, String description, UUID actorId) {
        log.warn("ANOMALY DETECTED in workspace {}: {} on {}:{}", workspaceId, description, aggregateType, aggregateId);

        // Publish domain event
        eventPublisher.publish(
                EventType.ANOMALY_DETECTED,
                workspaceId,
                aggregateType != null ? aggregateType : "SYSTEM",
                aggregateId != null ? aggregateId : workspaceId.toString(),
                EventSeverity.HIGH,
                Map.of("anomalyDescription", description, "detectedBy", "SecretAnomalyDetectionService")
        );

        // Create incident
        incidentService.createIncident(
                workspaceId,
                "Anomaly Alert: " + description,
                "Deterministic anomaly detection triggered: " + description + " on " + aggregateType + " " + aggregateId,
                IncidentSeverity.HIGH,
                "ANOMALY",
                null,
                actorId
        );
    }
}
