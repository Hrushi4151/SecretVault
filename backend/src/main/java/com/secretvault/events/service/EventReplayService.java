package com.secretvault.events.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.dispatcher.EventDispatcher;
import com.secretvault.events.entity.EventReplayRequest;
import com.secretvault.events.entity.OutboxEvent;
import com.secretvault.events.entity.ReplayStatus;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.repository.EventReplayRequestRepository;
import com.secretvault.events.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventReplayService {

    private static final Logger log = LoggerFactory.getLogger(EventReplayService.class);

    private final EventReplayRequestRepository replayRepository;
    private final OutboxEventRepository outboxRepository;
    private final EventDispatcher eventDispatcher;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public EventReplayService(
            EventReplayRequestRepository replayRepository,
            OutboxEventRepository outboxRepository,
            EventDispatcher eventDispatcher,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.replayRepository = replayRepository;
        this.outboxRepository = outboxRepository;
        this.eventDispatcher = eventDispatcher;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventReplayRequest requestReplay(
            UUID workspaceId,
            UUID targetEventId,
            String eventTypeFilter,
            String aggregateType,
            String aggregateId,
            Instant fromTimestamp,
            Instant toTimestamp,
            boolean reexecuteSideEffects,
            UUID actorId
    ) {
        verifyReplayAccess(workspaceId, reexecuteSideEffects, actorId);

        EventReplayRequest req = new EventReplayRequest();
        req.setWorkspaceId(workspaceId);
        req.setTargetEventId(targetEventId);
        req.setEventTypeFilter(eventTypeFilter);
        req.setAggregateType(aggregateType);
        req.setAggregateId(aggregateId);
        req.setFromTimestamp(fromTimestamp);
        req.setToTimestamp(toTimestamp);
        req.setReexecuteSideEffects(reexecuteSideEffects);
        req.setStatus(ReplayStatus.PROCESSING);
        req.setRequestedBy(actorId);

        EventReplayRequest saved = replayRepository.save(req);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.EVENT_REPLAY_REQUESTED,
                "EVENT_REPLAY",
                saved.getId(),
                null,
                null,
                "Replay requested with reexecuteSideEffects=" + reexecuteSideEffects
        );

        int replayedCount = 0;
        try {
            if (targetEventId != null) {
                OutboxEvent outbox = outboxRepository.findByEventId(targetEventId)
                        .filter(e -> e.getWorkspaceId().equals(workspaceId))
                        .orElseThrow(() -> ApiException.notFound("Event not found in outbox"));

                DomainEvent event = objectMapper.readValue(outbox.getPayload(), BaseDomainEvent.class);
                eventDispatcher.dispatchDomainEvent(event, reexecuteSideEffects);
                replayedCount = 1;
            } else {
                List<OutboxEvent> outboxList = outboxRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, Pageable.ofSize(100)).getContent();
                for (OutboxEvent outbox : outboxList) {
                    if (eventTypeFilter != null && !outbox.getEventType().equalsIgnoreCase(eventTypeFilter)) {
                        continue;
                    }
                    if (aggregateType != null && !outbox.getAggregateType().equalsIgnoreCase(aggregateType)) {
                        continue;
                    }
                    if (aggregateId != null && !outbox.getAggregateId().equalsIgnoreCase(aggregateId)) {
                        continue;
                    }
                    DomainEvent event = objectMapper.readValue(outbox.getPayload(), BaseDomainEvent.class);
                    eventDispatcher.dispatchDomainEvent(event, reexecuteSideEffects);
                    replayedCount++;
                }
            }

            saved.setStatus(ReplayStatus.COMPLETED);
            saved.setEventsReplayedCount(replayedCount);
            saved.setCompletedAt(Instant.now());
            saved = replayRepository.save(saved);

            auditService.recordAudit(
                    null,
                    workspaceId,
                    actorId,
                    "SYSTEM",
                    AuditAction.EVENT_REPLAY_COMPLETED,
                    "EVENT_REPLAY",
                    saved.getId(),
                    null,
                    null,
                    "Successfully replayed " + replayedCount + " events"
            );

        } catch (Exception e) {
            log.error("Failed to execute event replay request {}: {}", saved.getId(), e.getMessage(), e);
            saved.setStatus(ReplayStatus.FAILED);
            saved.setErrorMessage(e.getMessage());
            saved.setCompletedAt(Instant.now());
            saved = replayRepository.save(saved);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<EventReplayRequest> listReplayRequests(UUID workspaceId, Pageable pageable, UUID actorId) {
        verifyReadAccess(workspaceId, actorId);
        return replayRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    private void verifyReplayAccess(UUID workspaceId, boolean reexecuteSideEffects, UUID actorId) {
        if (actorId == null) return;
        AccessPermission perm = reexecuteSideEffects ? AccessPermission.AUTOMATION_MANAGE : AccessPermission.SECURITY_VIEW;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, perm, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing " + perm.getCode() + " for event replay");
        }
    }

    private void verifyReadAccess(UUID workspaceId, UUID actorId) {
        if (actorId == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.SECURITY_VIEW, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing WORKSPACE_AUDIT_READ");
        }
    }
}
