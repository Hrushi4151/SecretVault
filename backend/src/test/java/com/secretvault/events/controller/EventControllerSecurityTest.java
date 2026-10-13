package com.secretvault.events.controller;

import com.secretvault.access.model.AccessDecision;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 13: EventController Security & Multi-Tenant Isolation Tests")
class EventControllerSecurityTest {

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private EventDispatcher eventDispatcher;

    @Mock
    private EventReplayService replayService;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    private EventController controller;

    private UUID workspaceA;
    private UUID workspaceB;
    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        workspaceA = UUID.randomUUID();
        workspaceB = UUID.randomUUID();
        userId = UUID.randomUUID();
        principal = mock(UserPrincipal.class);
        lenient().when(principal.getId()).thenReturn(userId);

        controller = new EventController(
                domainEventRepository,
                outboxRepository,
                eventDispatcher,
                replayService,
                effectiveAccessService,
                auditService
        );
    }

    @Test
    @DisplayName("Negative RBAC: User without SECURITY_VIEW permission is denied 403 Forbidden")
    void testNegativeRbacViewEvents() {
        when(effectiveAccessService.evaluateAccess(eq(workspaceA), isNull(), isNull(), isNull(), eq(AccessPermission.SECURITY_VIEW), eq(userId)))
                .thenReturn(AccessDecision.deny(AccessPermission.SECURITY_VIEW, "No audit view permission"));

        assertThatThrownBy(() -> controller.listEvents(workspaceA, null, null, null, null, null, PageRequest.of(0, 20), principal))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("missing SECURITY_VIEW permission");
    }

    @Test
    @DisplayName("Negative RBAC: User without AUTOMATION_MANAGE cannot retry dead-letter event")
    void testNegativeRbacRetryDeadLetter() {
        UUID eventId = UUID.randomUUID();
        when(effectiveAccessService.evaluateAccess(eq(workspaceA), isNull(), isNull(), isNull(), eq(AccessPermission.AUTOMATION_MANAGE), eq(userId)))
                .thenReturn(AccessDecision.deny(AccessPermission.AUTOMATION_MANAGE, "No automation manage permission"));

        assertThatThrownBy(() -> controller.retryDeadLetterEvent(workspaceA, eventId, principal))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("missing AUTOMATION_MANAGE permission");

        verify(eventDispatcher, never()).retryDeadLetterEvent(any(), any());
    }

    @Test
    @DisplayName("Multi-Tenant Isolation: Queries enforce workspace scoping")
    void testTenantScoping() {
        when(effectiveAccessService.evaluateAccess(eq(workspaceA), isNull(), isNull(), isNull(), eq(AccessPermission.SECURITY_VIEW), eq(userId)))
                .thenReturn(AccessDecision.allow("Granted"));

        DomainEventEntity eventA = new DomainEventEntity();
        eventA.setWorkspaceId(workspaceA);
        eventA.setEventType("SECRET_CREATED");

        when(domainEventRepository.filterEvents(eq(workspaceA), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(eventA)));

        ResponseEntity<ApiResponse<Page<DomainEventEntity>>> response =
                controller.listEvents(workspaceA, null, null, null, null, null, PageRequest.of(0, 10), principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().getContent()).hasSize(1);
        assertThat(response.getBody().data().getContent().get(0).getWorkspaceId()).isEqualTo(workspaceA);

        // Verify repository was queried strictly with workspaceA
        verify(domainEventRepository).filterEvents(eq(workspaceA), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("Dead-letter retry: Authorized operator call resets event and records audit event")
    void testAuthorizedDeadLetterRetry() {
        UUID eventId = UUID.randomUUID();
        when(effectiveAccessService.evaluateAccess(eq(workspaceA), isNull(), isNull(), isNull(), eq(AccessPermission.AUTOMATION_MANAGE), eq(userId)))
                .thenReturn(AccessDecision.allow("Granted"));

        when(eventDispatcher.retryDeadLetterEvent(workspaceA, eventId)).thenReturn(true);

        ResponseEntity<ApiResponse<Boolean>> response = controller.retryDeadLetterEvent(workspaceA, eventId, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isTrue();

        verify(eventDispatcher, times(1)).retryDeadLetterEvent(workspaceA, eventId);
        verify(auditService, times(1)).recordAudit(
                isNull(), eq(workspaceA), eq(userId), eq("USER"),
                eq(AuditAction.EVENT_DEAD_LETTER_RETRIED), eq("OUTBOX_EVENT"),
                eq(eventId), isNull(), isNull(), contains("retried dead-letter")
        );
    }

    @Test
    @DisplayName("Outbox stats returns accurate health metrics")
    void testGetOutboxStats() {
        when(effectiveAccessService.evaluateAccess(eq(workspaceA), isNull(), isNull(), isNull(), eq(AccessPermission.SECURITY_VIEW), eq(userId)))
                .thenReturn(AccessDecision.allow("Granted"));

        OutboxStatsDto stats = new OutboxStatsDto(5, 1, 0, 0, 100, 12L);
        when(eventDispatcher.getOutboxStats(workspaceA)).thenReturn(stats);

        ResponseEntity<ApiResponse<OutboxStatsDto>> response = controller.getOutboxStats(workspaceA, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().pendingCount()).isEqualTo(5);
        assertThat(response.getBody().data().oldestPendingAgeSeconds()).isEqualTo(12L);
    }
}
