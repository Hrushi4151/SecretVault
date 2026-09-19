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
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private JitAccessService jitAccessService;

    private UUID workspaceId;
    private UUID requesterUserId;
    private UUID approverUserId;
    private UUID projectId;
    private UUID environmentId;

    private Project project;
    private Environment environment;
    private User requesterUser;
    private User approverUser;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        requesterUserId = UUID.randomUUID();
        approverUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();

        project = new Project(workspaceId, "Payment API", "payment-api", "desc", requesterUserId);
        project.setId(projectId);

        environment = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "desc", true, requesterUserId);
        environment.setId(environmentId);

        requesterUser = new User("dev@example.com", "hash", "Dev User");
        approverUser = new User("admin@example.com", "hash", "Admin User");
    }

    @Test
    @DisplayName("Submit JIT: Successfully submits temporary access request")
    void testSubmitJitRequestSuccess() {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, null, AccessPermission.SECRET_REVEAL, 60, "Production incident DB debugging"
        );

        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, requesterUserId)).thenReturn(true);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));

        JitAccessRequest saved = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, null, AccessPermission.SECRET_REVEAL, 60, "Production incident DB debugging"
        );
        when(jitRepository.save(any(JitAccessRequest.class))).thenReturn(saved);
        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));

        JitAccessRequestResponse response = jitAccessService.submitRequest(workspaceId, req, requesterUserId);

        assertNotNull(response);
        assertEquals(JitStatus.PENDING, response.status());
        assertEquals(60, response.durationMinutes());
    }

    @Test
    @DisplayName("Anti-Self-Approval: User cannot approve their own JIT request")
    void testAntiSelfApprovalRejection() {
        UUID requestId = UUID.randomUUID();
        JitAccessRequest request = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, null, AccessPermission.SECRET_REVEAL, 30, "Self approve test"
        );
        request.setId(requestId);

        when(jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)).thenReturn(Optional.of(request));

        ApiException ex = assertThrows(ApiException.class, () ->
                jitAccessService.approveRequest(workspaceId, requestId, new ApproveJitRequest("Approved"), requesterUserId));

        assertEquals("FORBIDDEN", ex.getCode());
        assertTrue(ex.getMessage().contains("Anti-Self-Approval Violation"));
    }

    @Test
    @DisplayName("Approve JIT: Distinct approver successfully approves and assigns TTL")
    void testApproveJitSuccess() {
        UUID requestId = UUID.randomUUID();
        JitAccessRequest request = new JitAccessRequest(
                workspaceId, requesterUserId, projectId, environmentId, null, AccessPermission.SECRET_REVEAL, 30, "Incident remediation"
        );
        request.setId(requestId);

        when(jitRepository.findByIdAndWorkspaceIdForUpdate(requestId, workspaceId)).thenReturn(Optional.of(request));
        when(jitRepository.save(any(JitAccessRequest.class))).thenReturn(request);
        when(userRepository.findById(requesterUserId)).thenReturn(Optional.of(requesterUser));
        when(userRepository.findById(approverUserId)).thenReturn(Optional.of(approverUser));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        JitAccessRequestResponse response = jitAccessService.approveRequest(
                workspaceId, requestId, new ApproveJitRequest("Dual custody approved"), approverUserId
        );

        assertNotNull(response);
        assertEquals(JitStatus.APPROVED, response.status());
        assertNotNull(response.approvedAt());
        assertNotNull(response.expiresAt());
        verify(effectiveAccessService).checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.JIT_APPROVE, approverUserId);
    }
}
