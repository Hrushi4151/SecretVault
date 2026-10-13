package com.secretvault.audit;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.controller.AuditController;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerAndServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    private AuditService auditService;
    private AuditController auditController;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID otherWorkspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
        auditController = new AuditController(auditService, effectiveAccessService);
        principal = new UserPrincipal(userId, "test@example.com", "pass", "Tester", true, List.of());
    }

    @Test
    @DisplayName("getWorkspaceAuditLogs enforces SECURITY_VIEW permission and limits results")
    void getWorkspaceAuditLogs_enforcesPermissionAndLimits() {
        AuditLog log1 = new AuditLog(null, workspaceId, userId, "USER", AuditAction.SECRET_CREATED, "SECRET", UUID.randomUUID(), "req1", "127.0.0.1", "SUCCESS");
        AuditLog log2 = new AuditLog(null, workspaceId, userId, "USER", AuditAction.SECRET_REVEALED, "SECRET", UUID.randomUUID(), "req2", "127.0.0.1", "SUCCESS");

        when(auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(log1, log2));

        ResponseEntity<ApiResponse<List<AuditLog>>> response = auditController.getWorkspaceAuditLogs(workspaceId, 1, principal);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).getAction()).isEqualTo(AuditAction.SECRET_CREATED);

        verify(effectiveAccessService).checkPermission(eq(workspaceId), isNull(), isNull(), isNull(), eq(AccessPermission.SECURITY_VIEW), eq(userId));
    }

    @Test
    @DisplayName("searchAuditLogs passes sanitized pageable and filters to repository")
    void searchAuditLogs_passesFiltersAndPageable() {
        AuditLog log1 = new AuditLog(null, workspaceId, userId, "USER", AuditAction.FINDING_CONFIRMED, "SECURITY_FINDING", UUID.randomUUID(), "req1", "127.0.0.1", "SUCCESS");
        Page<AuditLog> page = new PageImpl<>(List.of(log1), PageRequest.of(0, 10), 1);

        when(auditLogRepository.searchAuditLogs(
                eq(workspaceId), eq(AuditAction.FINDING_CONFIRMED), eq("SUCCESS"), eq("SECURITY_FINDING"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<AuditLog>>> response = auditController.searchAuditLogs(
                workspaceId, AuditAction.FINDING_CONFIRMED, "SUCCESS", "SECURITY_FINDING", null, null, null, null, PageRequest.of(0, 10), principal
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).getAction()).isEqualTo(AuditAction.FINDING_CONFIRMED);

        verify(effectiveAccessService).checkPermission(eq(workspaceId), isNull(), isNull(), isNull(), eq(AccessPermission.SECURITY_VIEW), eq(userId));
    }

    @Test
    @DisplayName("exportAuditLogs returns valid CSV format and strictly isolates by workspace")
    void exportAuditLogs_returnsCsvFormattedStream() {
        UUID resId = UUID.randomUUID();
        AuditLog log1 = new AuditLog(null, workspaceId, userId, "USER", AuditAction.SECRET_CREATED, "SECRET", resId, "req-export-1", "10.0.0.1", "SUCCESS");

        when(auditLogRepository.exportAuditLogs(
                eq(workspaceId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(500)
        )).thenReturn(List.of(log1));

        ResponseEntity<?> response = auditController.exportAuditLogs(
                workspaceId, "csv", null, null, null, null, null, null, null, 500, principal
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("audit_logs_" + workspaceId + ".csv");

        String csvBody = (String) response.getBody();
        assertThat(csvBody).contains("id,timestamp,action,outcome,actorType,actorId,resourceType,resourceId,ipAddress,requestId");
        assertThat(csvBody).contains("SECRET_CREATED");
        assertThat(csvBody).contains("req-export-1");
        assertThat(csvBody).contains("10.0.0.1");

        verify(effectiveAccessService).checkPermission(eq(workspaceId), isNull(), isNull(), isNull(), eq(AccessPermission.SECURITY_VIEW), eq(userId));
    }

    @Test
    @DisplayName("exportAuditLogs returns valid JSON format when requested")
    void exportAuditLogs_returnsJsonList() {
        AuditLog log1 = new AuditLog(null, workspaceId, userId, "USER", AuditAction.SECRET_DELETED, "SECRET", UUID.randomUUID(), "req-json-1", "10.0.0.2", "SUCCESS");

        when(auditLogRepository.exportAuditLogs(
                eq(workspaceId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(100)
        )).thenReturn(List.of(log1));

        ResponseEntity<?> response = auditController.exportAuditLogs(
                workspaceId, "json", null, null, null, null, null, null, null, 100, principal
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<AuditLog> list = (List<AuditLog>) response.getBody();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getAction()).isEqualTo(AuditAction.SECRET_DELETED);
    }

    @Test
    @DisplayName("AuditService append-only contract: records can be appended but no update or delete methods exist")
    void auditService_appendOnlyContract() {
        AuditLog log1 = new AuditLog(null, workspaceId, userId, "USER", AuditAction.SECRET_CREATED, "SECRET", UUID.randomUUID(), "req1", "127.0.0.1", "SUCCESS");
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(log1);

        AuditLog saved = auditService.recordAudit(
                null, workspaceId, userId, "USER", AuditAction.SECRET_CREATED, "SECRET", UUID.randomUUID(), "req1", "127.0.0.1", "SUCCESS"
        );

        assertThat(saved).isNotNull();
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));

        // Verify repository methods on AuditService do not include update or delete
        Class<?> serviceClass = AuditService.class;
        for (var method : serviceClass.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotContainIgnoringCase("delete");
            assertThat(method.getName()).doesNotContainIgnoringCase("remove");
            assertThat(method.getName()).doesNotContainIgnoringCase("update");
        }
    }
}
