package com.secretvault.access.grant.service;

import com.secretvault.access.grant.dto.AccessGrantResponse;
import com.secretvault.access.grant.dto.CreateAccessGrantRequest;
import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
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
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessGrantServiceTest {

    @Mock
    private AccessGrantRepository accessGrantRepository;

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
    private AccessGrantService accessGrantService;

    private UUID workspaceId;
    private UUID targetUserId;
    private UUID actorUserId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;

    private Project project;
    private Environment environment;
    private Secret secret;
    private User user;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();

        project = new Project(workspaceId, "Billing API", "billing-api", "Core", actorUserId);
        project.setId(projectId);

        environment = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "Prod", true, actorUserId);
        environment.setId(environmentId);

        secret = new Secret(environmentId, "DB_PASS", "Secret", actorUserId);
        user = new User("developer@example.com", "hash", "Dev User");
    }

    @Test
    @DisplayName("Create Grant: Successfully grants secret-level permission when hierarchy is valid")
    void testCreateGrantSuccess() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL
        );

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(accessGrantRepository.existsByWorkspaceIdAndUserIdAndScopeTypeAndProjectIdAndEnvironmentIdAndSecretIdAndPermission(
                workspaceId, targetUserId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL
        )).thenReturn(false);

        AccessGrant savedGrant = new AccessGrant(
                workspaceId, targetUserId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, actorUserId
        );
        when(accessGrantRepository.save(any(AccessGrant.class))).thenReturn(savedGrant);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));

        AccessGrantResponse response = accessGrantService.createGrant(workspaceId, request, actorUserId);

        assertNotNull(response);
        assertEquals(AccessScope.SECRET, response.scopeType());
        assertEquals(AccessPermission.SECRET_REVEAL, response.permission());
        verify(effectiveAccessService).checkPermission(workspaceId, projectId, environmentId, secretId, AccessPermission.ACCESS_MANAGE, actorUserId);
    }

    @Test
    @DisplayName("Scope Constraint: WORKSPACE scope with non-null projectId is rejected")
    void testWorkspaceScopeWithProjectIdRejected() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.WORKSPACE, projectId, null, null, AccessPermission.SECRET_READ
        );

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)));

        ApiException ex = assertThrows(ApiException.class, () ->
                accessGrantService.createGrant(workspaceId, request, actorUserId));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("WORKSPACE scope grants must have null projectId"));
    }

    @Test
    @DisplayName("Revoke Grant: Successfully deletes grant when authorized")
    void testRevokeGrantSuccess() {
        UUID grantId = UUID.randomUUID();
        AccessGrant grant = new AccessGrant(
                workspaceId, targetUserId, AccessScope.ENVIRONMENT, projectId, environmentId, null, AccessPermission.SECRET_UPDATE, actorUserId
        );
        grant.setId(grantId);

        when(accessGrantRepository.findByIdAndWorkspaceId(grantId, workspaceId)).thenReturn(Optional.of(grant));

        accessGrantService.revokeGrant(workspaceId, grantId, actorUserId);

        verify(accessGrantRepository).delete(grant);
        verify(auditService).logSuccess(eq(com.secretvault.audit.entity.AuditAction.ACCESS_GRANT_REVOKED), any(), eq(grantId), eq(actorUserId), eq(workspaceId), any());
    }
}
