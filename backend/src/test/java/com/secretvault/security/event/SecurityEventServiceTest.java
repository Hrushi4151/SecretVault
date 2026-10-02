package com.secretvault.security.event;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.security.event.dto.RecordSecurityEventRequest;
import com.secretvault.security.event.dto.SecurityEventResponse;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityEventServiceTest {

    @Mock
    private SecurityEventRepository eventRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    private SecurityEventService eventService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eventService = new SecurityEventService(eventRepository, workspaceRepository, effectiveAccessService);
    }

    @Test
    @DisplayName("Should record security event with sanitized metadata")
    void testRecordEvent() {
        when(eventRepository.save(any(SecurityEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordSecurityEventRequest req = new RecordSecurityEventRequest(
                SecurityEventType.SECRET_REVEALED,
                SecurityEventSeverity.MEDIUM,
                SecurityEventOutcome.SUCCESS,
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId,
                "API",
                "127.0.0.1",
                "Mozilla/5.0",
                "req-123",
                Map.of("targetResource", "DB_CONFIG", "token", "should_be_redacted")
        );

        SecurityEventResponse response = eventService.recordEvent(workspaceId, req);

        assertNotNull(response);
        assertEquals(SecurityEventType.SECRET_REVEALED, response.eventType());
        assertEquals(SecurityEventSeverity.MEDIUM, response.severity());
        assertEquals(SecurityEventOutcome.SUCCESS, response.outcome());
        assertEquals("DB_CONFIG", response.metadata().get("targetResource"));
        assertEquals("[REDACTED_SENSITIVE_FIELD]", response.metadata().get("token"));

        verify(eventRepository, times(1)).save(any(SecurityEvent.class));
    }

    @Test
    @DisplayName("Should retrieve events when authorized by EffectiveAccessService")
    void testGetEventsAuthorized() {
        SecurityEvent mockEvent = new SecurityEvent(
                workspaceId, null, null, userId,
                SecurityEventType.MEMBER_ROLE_CHANGED,
                SecurityEventSeverity.HIGH,
                SecurityEventOutcome.SUCCESS,
                "ADMIN_CONSOLE",
                "10.0.0.1",
                "agent",
                "req-999",
                "{\"role\":\"ADMIN\"}"
        );

        Page<SecurityEvent> page = new PageImpl<>(List.of(mockEvent));
        when(eventRepository.searchEvents(eq(workspaceId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        Page<SecurityEventResponse> result = eventService.getEvents(
                workspaceId, null, null, null,
                null, null, null, null, null,
                userId, PageRequest.of(0, 20)
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(SecurityEventType.MEMBER_ROLE_CHANGED, result.getContent().get(0).eventType());

        verify(effectiveAccessService, times(1)).checkPermission(
                eq(workspaceId), isNull(), isNull(), isNull(),
                eq(AccessPermission.SECURITY_VIEW), eq(userId)
        );
    }
}
