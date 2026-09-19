package com.secretvault.access.jit.service;

import com.secretvault.access.jit.dto.ApproveJitRequest;
import com.secretvault.access.jit.dto.JitAccessRequestResponse;
import com.secretvault.access.jit.dto.RejectJitRequest;
import com.secretvault.access.jit.dto.SubmitJitRequest;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.dto.PageResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JitAccessServiceTest {

    @Mock
    private JitAccessRequestRepository jitRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    private Clock fixedClock;
    private Instant fixedInstant;

    private JitAccessService jitAccessService;

    private UUID workspaceId;
    private UUID requesterUserId;
    private UUID approverUserId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;

    private Project project;
    private Environment environment;
    private Environment devEnvironment;
    private Secret secret;
    private Secret deletedSecret;
    private User requesterUser;
    private User approverUser;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        requesterUserId = UUID.randomUUID();
        approverUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();

        fixedInstant = Instant.parse("2026-09-19T18:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        jitAccessService = new JitAccessService(
                jitRepository,
                membershipRepository,
                projectRepository,
                environmentRepository,
                secretRepository,
                userRepository,
                auditService,
                effectiveAccessService,
                fixedClock
        );

        project = new Project(workspaceId, "Payment API", "payment-api", "desc", requesterUserId);
        project.setId(projectId);

        environment = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "desc", true, requesterUserId);
        environment.setId(environmentId);

        devEnvironment = new Environment(projectId, "Development", "dev", EnvType.DEVELOPMENT, "desc", false, requesterUserId);
        devEnvironment.setId(UUID.randomUUID());

        secret = new Secret(environmentId, "STRIPE_KEY", "desc", requesterUserId);
        secret.setId(secretId);

        deletedSecret = new Secret(environmentId, "OLD_KEY", "desc", requesterUserId);
        deletedSecret.setId(UUID.randomUUID());
        deletedSecret.setStatus(SecretStatus.DELETED);

        requesterUser = new User("dev@example.com", "hash", "Dev User");
        requesterUser.setId(requesterUserId);

        approverUser = new User("admin@example.com", "hash", "Admin User");
        approverUser.setId(approverUserId);
    }

    @Test
    @DisplayName("Submit JIT: Successfully creates PENDING request")
    void testSubmitJitRequestSuccess() {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Investigating production latency"
        );

        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, requesterUserId)).thenReturn(true);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(jitRepository.existsPendingRequest(workspaceId, requesterUserId, environmentId, secretId, AccessPermission.SECRET_REVEAL)).thenReturn(false);

        JitAccessRequest saved = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Investigating production latency"
        );
        saved.setId(UUID.randomUUID());
        when(jitRepository.save(any(JitAccessRequest.class))).thenReturn(saved);
        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));

        JitAccessRequestResponse response = jitAccessService.submitRequest(workspaceId, req, requesterUserId);

        assertNotNull(response);
        assertEquals(JitStatus.PENDING, response.status());
        assertEquals("dev@example.com", response.userEmail());
        verify(auditService).logSuccess(eq(AuditAction.JIT_ACCESS_REQUESTED), any(), eq(saved.getId()), eq(requesterUserId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Submit JIT: Duplicate pending request rejected with 409 Conflict")
    void testSubmitJitRequestDuplicateConflict() {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Investigating production latency"
        );

        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, requesterUserId)).thenReturn(true);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(jitRepository.existsPendingRequest(workspaceId, requesterUserId, environmentId, secretId, AccessPermission.SECRET_REVEAL)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                jitAccessService.submitRequest(workspaceId, req, requesterUserId));

        assertEquals("RESOURCE_CONFLICT", ex.getCode());
        assertTrue(ex.getMessage().contains("pending JIT request already exists"));
    }

    @Test
    @DisplayName("Submit JIT: Deleted secret rejected")
    void testSubmitJitRequestDeletedSecretRejected() {
        UUID delSecId = deletedSecret.getId();
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, delSecId, AccessPermission.SECRET_READ, 30, "Investigating production latency"
        );

        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, requesterUserId)).thenReturn(true);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(secretRepository.findByIdAndEnvironmentId(delSecId, environmentId)).thenReturn(Optional.of(deletedSecret));

        ApiException ex = assertThrows(ApiException.class, () ->
                jitAccessService.submitRequest(workspaceId, req, requesterUserId));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("deleted secret"));
    }

    @Test
    @DisplayName("Approve JIT: Successfully transitions to APPROVED with server-calculated expiry")
    void testApproveRequestSuccess() {
        UUID requestId = UUID.randomUUID();
        JitAccessRequest req = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 45, "Emergency incident fix"
        );
        req.setId(requestId);

        when(jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)).thenReturn(Optional.of(req));
        when(jitRepository.save(any(JitAccessRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(userRepository.findById(approverUserId)).thenReturn(Optional.of(approverUser));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        JitAccessRequestResponse response = jitAccessService.approveRequest(
                workspaceId, requestId, new ApproveJitRequest("Approved for incident fix"), approverUserId
        );

        assertNotNull(response);
        assertEquals(JitStatus.APPROVED, response.status());
        assertEquals(fixedInstant, response.approvedAt());
        assertEquals(fixedInstant.plus(Duration.ofMinutes(45)), response.expiresAt());
        verify(effectiveAccessService).checkPermission(workspaceId, projectId, environmentId, secretId, AccessPermission.JIT_APPROVE, approverUserId);
        verify(auditService).logSuccess(eq(AuditAction.JIT_ACCESS_APPROVED), any(), eq(requestId), eq(approverUserId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Anti-Self-Approval: Requester attempting to approve own request is blocked with 403")
    void testAntiSelfApprovalViolation() {
        UUID requestId = UUID.randomUUID();
        JitAccessRequest req = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Self elevation attempt"
        );
        req.setId(requestId);

        when(jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)).thenReturn(Optional.of(req));

        ApiException ex = assertThrows(ApiException.class, () ->
                jitAccessService.approveRequest(workspaceId, requestId, new ApproveJitRequest("Self approval"), requesterUserId));

        assertEquals("FORBIDDEN", ex.getCode());
        assertTrue(ex.getMessage().contains("Anti-Self-Approval Violation"));
    }

    @Test
    @DisplayName("Reject JIT: Successfully transitions PENDING request to REJECTED")
    void testRejectRequestSuccess() {
        UUID requestId = UUID.randomUUID();
        JitAccessRequest req = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Need access"
        );
        req.setId(requestId);

        when(jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)).thenReturn(Optional.of(req));
        when(jitRepository.save(any(JitAccessRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        JitAccessRequestResponse response = jitAccessService.rejectRequest(
                workspaceId, requestId, new RejectJitRequest("Insufficient justification"), approverUserId
        );

        assertNotNull(response);
        assertEquals(JitStatus.REJECTED, response.status());
        assertEquals("Insufficient justification", response.reviewerNotes());
        verify(auditService).logSuccess(eq(AuditAction.JIT_ACCESS_REJECTED), any(), eq(requestId), eq(approverUserId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Cancel JIT: Requester can cancel their own PENDING request")
    void testCancelRequestSuccess() {
        UUID requestId = UUID.randomUUID();
        JitAccessRequest req = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Accidental request"
        );
        req.setId(requestId);

        when(jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)).thenReturn(Optional.of(req));

        jitAccessService.cancelRequest(workspaceId, requestId, requesterUserId);

        assertEquals(JitStatus.CANCELLED, req.getStatus());
        verify(auditService).logSuccess(eq(AuditAction.JIT_ACCESS_CANCELLED), any(), eq(requestId), eq(requesterUserId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Cancel JIT: Non-requester attempting to cancel is blocked with 403")
    void testCancelRequestNonRequesterForbidden() {
        UUID requestId = UUID.randomUUID();
        JitAccessRequest req = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Pending req"
        );
        req.setId(requestId);

        when(jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)).thenReturn(Optional.of(req));

        ApiException ex = assertThrows(ApiException.class, () ->
                jitAccessService.cancelRequest(workspaceId, requestId, approverUserId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Revoke JIT: Instantly revokes an active APPROVED grant")
    void testRevokeGrantSuccess() {
        UUID requestId = UUID.randomUUID();
        JitAccessRequest req = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Active grant"
        );
        req.setId(requestId);
        req.setStatus(JitStatus.APPROVED);

        when(jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)).thenReturn(Optional.of(req));

        jitAccessService.revokeGrant(workspaceId, requestId, approverUserId);

        assertEquals(JitStatus.REVOKED, req.getStatus());
        assertEquals(fixedInstant, req.getRevokedAt());
        verify(auditService).logSuccess(eq(AuditAction.JIT_ACCESS_REVOKED), any(), eq(requestId), eq(approverUserId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("List JIT: Paginated requests listing")
    void testListRequestsPaginated() {
        JitAccessRequest req = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Active grant"
        );
        req.setId(UUID.randomUUID());

        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, approverUserId)).thenReturn(true);
        when(jitRepository.findFilteredRequests(
                eq(workspaceId), eq(JitStatus.PENDING), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(req), PageRequest.of(0, 20), 1));
        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        PageResponse<JitAccessRequestResponse> response = jitAccessService.listRequestsPaginated(
                workspaceId, JitStatus.PENDING, null, null, null, null, PageRequest.of(0, 20), approverUserId
        );

        assertNotNull(response);
        assertEquals(1, response.totalElements());
        assertEquals(1, response.content().size());
    }
}
