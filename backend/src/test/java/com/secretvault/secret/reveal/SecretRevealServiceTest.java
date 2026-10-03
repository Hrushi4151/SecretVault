package com.secretvault.secret.reveal;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.auth.stepup.service.StepUpAuthenticationService;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.dto.SecretRevealResponse;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.secret.reveal.dto.*;
import com.secretvault.secret.reveal.entity.SecretRevealPolicy;
import com.secretvault.secret.reveal.model.RevealPolicyLevel;
import com.secretvault.secret.reveal.model.SecretRevealIntentPayload;
import com.secretvault.secret.reveal.model.SecretRevealPolicyEvaluation;
import com.secretvault.secret.reveal.repository.SecretRevealPolicyRepository;
import com.secretvault.secret.reveal.service.DefaultSecretRevealPolicyService;
import com.secretvault.secret.reveal.service.DefaultSecretRevealService;
import com.secretvault.secret.reveal.service.SecretRevealPolicyService;
import com.secretvault.secret.reveal.service.SecretRevealService;
import com.secretvault.secret.service.SecretAuthorizationHelper;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SecretRevealServiceTest {

    @Mock private SecretRepository secretRepository;
    @Mock private SecretVersionRepository secretVersionRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private SecretAuthorizationHelper authHelper;
    @Mock private StepUpAuthenticationService stepUpAuthenticationService;
    @Mock private SecurityStateStore securityStateStore;
    @Mock private AuditService auditService;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SessionService sessionService;
    @Mock private SecurityEventService securityEventService;
    @Mock private SecretRevealPolicyRepository policyRepository;

    private SecretRevealPolicyService policyService;
    private SecretRevealService revealService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID userId;

    private Secret secret;
    private SecretVersion version1;
    private User user;
    private UserSession session;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        userId = UUID.randomUUID();

        secret = new Secret(environmentId, "DATABASE_URL", "Database Connection String", userId);
        secret.setId(secretId);
        secret.setCurrentVersionNumber(1);
        secret.setStatus(SecretStatus.ACTIVE);

        version1 = new SecretVersion(
                secretId, 1,
                "cipher-db-url".getBytes(StandardCharsets.UTF_8),
                "enc-dek".getBytes(StandardCharsets.UTF_8),
                "iv-bytes".getBytes(StandardCharsets.UTF_8),
                "tag-bytes".getBytes(StandardCharsets.UTF_8),
                "key-ref-1",
                userId,
                "Initial version"
        );

        user = new User("dev@secretvault.io", "hash", "Developer User");
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        session = new UserSession(userId, "sess_123", null, "127.0.0.1", "Browser", "Mac", "Chrome", "macOS", Instant.now().plusSeconds(3600));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionService.findByIdentifier("sess_123")).thenReturn(Optional.of(session));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(version1));

        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setOrganizationId(UUID.randomUUID());
        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN);
        Project project = new Project(workspaceId, "Core", "core", "Core Project", userId);
        Environment environment = new Environment(projectId, "staging", "staging", EnvType.STAGING, "Staging env", false, userId);

        SecretAuthorizationHelper.WorkspaceContext wsContext =
                new SecretAuthorizationHelper.WorkspaceContext(workspace, membership, project, environment);
        when(authHelper.verifyHierarchy(any(), any(), any(), any())).thenReturn(wsContext);
        when(authHelper.verifyHierarchyAndRevealAccess(any(), any(), any(), eq(userId))).thenReturn(wsContext);

        when(encryptionService.decrypt(any(), anyString()))
                .thenReturn("postgres://admin:secret@localhost:5432/db".getBytes(StandardCharsets.UTF_8));

        policyService = new DefaultSecretRevealPolicyService(
                policyRepository, environmentRepository, effectiveAccessService, auditService
        );

        revealService = new DefaultSecretRevealService(
                secretRepository, secretVersionRepository, encryptionService, effectiveAccessService,
                authHelper, policyService, stepUpAuthenticationService, securityStateStore,
                auditService, userRepository, auditLogRepository, environmentRepository,
                projectRepository, sessionService, securityEventService
        );
    }

    @Test
    @DisplayName("Create custom reveal policy and inspect effective evaluation")
    void testCustomPolicyLifecycle() {
        SecretRevealPolicy customPolicy = new SecretRevealPolicy();
        customPolicy.setId(UUID.randomUUID());
        customPolicy.setWorkspaceId(workspaceId);
        customPolicy.setScopeType(PrivilegedPolicyScope.WORKSPACE);
        customPolicy.setPolicyLevel(RevealPolicyLevel.HIGHLY_SENSITIVE);
        customPolicy.setRequireStepUp(true);
        customPolicy.setRequireReason(true);
        customPolicy.setMinReasonLength(15);
        customPolicy.setMaxDisplayDurationSeconds(45);
        customPolicy.setCopyAllowed(true);

        when(policyRepository.findMatchingPolicies(workspaceId, projectId, environmentId, secretId))
                .thenReturn(List.of(customPolicy));
        when(policyRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(customPolicy));
        when(policyRepository.save(any(SecretRevealPolicy.class))).thenAnswer(i -> i.getArgument(0));

        SecretRevealPolicyEvaluation eval = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
        assertEquals(RevealPolicyLevel.HIGHLY_SENSITIVE, eval.policyLevel());
        assertTrue(eval.requireStepUp());
        assertTrue(eval.requireReason());
        assertEquals(15, eval.minReasonLength());
        assertEquals(45, eval.maxDisplayDurationSeconds());

        List<SecretRevealPolicyResponse> policies = policyService.getWorkspacePolicies(workspaceId, userId);
        assertEquals(1, policies.size());
        assertEquals(RevealPolicyLevel.HIGHLY_SENSITIVE, policies.get(0).policyLevel());
    }

    @Test
    @DisplayName("Audit history retrieval with pagination and actor resolution")
    void testAuditHistoryRetrieval() {
        AuditLog auditLog = new AuditLog(
                UUID.randomUUID(), workspaceId, userId, "USER",
                AuditAction.SECRET_REVEALED, "SECRET", secretId, "req-xyz", "127.0.0.1", "SUCCESS"
        );

        when(auditLogRepository.findByWorkspaceIdAndActionInOrderByCreatedAtDesc(eq(workspaceId), anyList(), any()))
                .thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1));

        Page<SecretRevealAuditResponse> result = revealService.getRevealAuditHistory(workspaceId, userId, PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("dev@secretvault.io", result.getContent().get(0).actorEmail());
        assertEquals("SECRET_REVEALED", result.getContent().get(0).action());
    }
}
