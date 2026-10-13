package com.secretvault.events.controller;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.dispatcher.EventDispatcher;
import com.secretvault.events.dto.OutboxStatsDto;
import com.secretvault.events.entity.DomainEventEntity;
import com.secretvault.events.entity.EventReplayRequest;
import com.secretvault.events.entity.OutboxEvent;
import com.secretvault.events.entity.OutboxStatus;
import com.secretvault.events.repository.DomainEventRepository;
import com.secretvault.events.repository.OutboxEventRepository;
import com.secretvault.events.service.EventReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "Events & Outbox", description = "Authoritative domain event querying, transactional outbox operations, and dead-letter recovery")
@SecurityRequirement(name = "BearerAuth")
public class EventController {

    private final DomainEventRepository domainEventRepository;
    private final OutboxEventRepository outboxRepository;
    private final EventDispatcher eventDispatcher;
    private final EventReplayService replayService;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;

    public EventController(
            DomainEventRepository domainEventRepository,
            OutboxEventRepository outboxRepository,
            EventDispatcher eventDispatcher,
            EventReplayService replayService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) EffectiveAccessService effectiveAccessService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AuditService auditService
    ) {
        this.domainEventRepository = domainEventRepository;
        this.outboxRepository = outboxRepository;
        this.eventDispatcher = eventDispatcher;
        this.replayService = replayService;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
    }

    @GetMapping("/events")
    @Operation(summary = "List authoritative immutable domain events in workspace")
    public ResponseEntity<ApiResponse<Page<DomainEventEntity>>> listEvents(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toTime,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        verifyReadAccess(workspaceId, principal);
        Page<DomainEventEntity> events = domainEventRepository.filterEvents(
                workspaceId, eventType, aggregateType, aggregateId, fromTime, toTime, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get single domain event by eventId")
    public ResponseEntity<ApiResponse<?>> getEvent(
            @PathVariable UUID workspaceId,
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        verifyReadAccess(workspaceId, principal);

        var domainOpt = domainEventRepository.findByWorkspaceIdAndEventId(workspaceId, eventId);
        if (domainOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(domainOpt.get()));
        }

        var outboxOpt = outboxRepository.findByWorkspaceIdAndEventId(workspaceId, eventId);
        if (outboxOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(outboxOpt.get()));
        }

        throw ApiException.notFound("Event not found in workspace");
    }

    @GetMapping("/events/stats")
    @Operation(summary = "Get transactional outbox backlog and processing statistics")
    public ResponseEntity<ApiResponse<OutboxStatsDto>> getOutboxStats(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        verifyReadAccess(workspaceId, principal);
        OutboxStatsDto stats = eventDispatcher.getOutboxStats(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/events/dead-letter")
    @Operation(summary = "List dead-lettered outbox events requiring operator attention")
    public ResponseEntity<ApiResponse<Page<OutboxEvent>>> listDeadLetterEvents(
            @PathVariable UUID workspaceId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        verifyReadAccess(workspaceId, principal);
        Page<OutboxEvent> deadLetterEvents = outboxRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(
                workspaceId, OutboxStatus.DEAD_LETTER, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(deadLetterEvents));
    }

    @PostMapping("/events/dead-letter/{eventId}/retry")
    @Operation(summary = "Retry a dead-letter outbox event with reset attempt count and immediate availability")
    public ResponseEntity<ApiResponse<Boolean>> retryDeadLetterEvent(
            @PathVariable UUID workspaceId,
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        verifyManageAccess(workspaceId, principal);

        boolean retried = eventDispatcher.retryDeadLetterEvent(workspaceId, eventId);

        if (auditService != null) {
            UUID actorId = principal != null ? principal.getId() : null;
            auditService.recordAudit(
                    null,
                    workspaceId,
                    actorId,
                    "USER",
                    AuditAction.EVENT_DEAD_LETTER_RETRIED,
                    "OUTBOX_EVENT",
                    eventId,
                    null,
                    null,
                    "Operator retried dead-letter event"
            );
        }

        return ResponseEntity.ok(ApiResponse.success(retried, "Event reset to PENDING for outbox redelivery"));
    }

    public record ReplayEventRequest(
            UUID targetEventId,
            String eventTypeFilter,
            String aggregateType,
            String aggregateId,
            Instant fromTimestamp,
            Instant toTimestamp,
            boolean reexecuteSideEffects
    ) {}

    @PostMapping({"/event-replays", "/events/replay"})
    @Operation(summary = "Request replay of domain events")
    public ResponseEntity<ApiResponse<EventReplayRequest>> requestReplay(
            @PathVariable UUID workspaceId,
            @RequestBody ReplayEventRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        EventReplayRequest result = replayService.requestReplay(
                workspaceId,
                request.targetEventId(),
                request.eventTypeFilter(),
                request.aggregateType(),
                request.aggregateId(),
                request.fromTimestamp(),
                request.toTimestamp(),
                request.reexecuteSideEffects(),
                actorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result, "Event replay completed"));
    }

    @GetMapping("/event-replays")
    @Operation(summary = "List event replay requests")
    public ResponseEntity<ApiResponse<Page<EventReplayRequest>>> listReplays(
            @PathVariable UUID workspaceId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<EventReplayRequest> results = replayService.listReplayRequests(workspaceId, pageable, actorId);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    private void verifyReadAccess(UUID workspaceId, UserPrincipal principal) {
        if (principal == null || effectiveAccessService == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.SECURITY_VIEW, principal.getId());
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing SECURITY_VIEW permission for domain events");
        }
    }

    private void verifyManageAccess(UUID workspaceId, UserPrincipal principal) {
        if (principal == null || effectiveAccessService == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.AUTOMATION_MANAGE, principal.getId());
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing AUTOMATION_MANAGE permission for outbox triage");
        }
    }
}
