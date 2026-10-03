package com.secretvault.events.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.events.entity.EventReplayRequest;
import com.secretvault.events.entity.OutboxEvent;
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
@Tag(name = "Events & Outbox", description = "Domain event querying and event replay operations")
@SecurityRequirement(name = "BearerAuth")
public class EventController {

    private final OutboxEventRepository outboxRepository;
    private final EventReplayService replayService;

    public EventController(OutboxEventRepository outboxRepository, EventReplayService replayService) {
        this.outboxRepository = outboxRepository;
        this.replayService = replayService;
    }

    @GetMapping("/events")
    @Operation(summary = "List domain events in workspace")
    public ResponseEntity<ApiResponse<Page<OutboxEvent>>> listEvents(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<OutboxEvent> events;
        if (eventType != null) {
            events = outboxRepository.findByWorkspaceIdAndEventTypeOrderByCreatedAtDesc(workspaceId, eventType, pageable);
        } else if (aggregateType != null && aggregateId != null) {
            events = outboxRepository.findByWorkspaceIdAndAggregateTypeAndAggregateIdOrderByCreatedAtDesc(workspaceId, aggregateType, aggregateId, pageable);
        } else {
            events = outboxRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get single domain event by eventId")
    public ResponseEntity<ApiResponse<OutboxEvent>> getEvent(
            @PathVariable UUID workspaceId,
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OutboxEvent event = outboxRepository.findByEventId(eventId)
                .filter(e -> e.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> com.secretvault.common.exception.ApiException.notFound("Event not found"));
        return ResponseEntity.ok(ApiResponse.success(event));
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

    @PostMapping("/event-replays")
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
}
