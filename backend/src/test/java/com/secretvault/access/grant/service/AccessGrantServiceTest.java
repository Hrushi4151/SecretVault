package com.secretvault.access.grant.service;

import com.secretvault.access.grant.dto.AccessGrantResponse;
import com.secretvault.access.grant.dto.CreateAccessGrantRequest;
import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private Environment devEnvironment;
    private Environment prodEnvironment;
    private Secret activeSecret;
    private Secret deletedSecret;
    private User targetUser;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();

        project = new Project(workspaceId, "Payment Service", "payment-service", "Billing", actorUserId);
        project.setId(projectId);

        devEnvironment = new Environment(projectId, "Development", "dev", EnvType.DEVELOPMENT, "Dev env", false, actorUserId);
        devEnvironment.setId(environmentId);

        prodEnvironment = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "Prod env", true, actorUserId);
        prodEnvironment.setId(environmentId);

        activeSecret = new Secret(environmentId, "STRIPE_KEY", "Stripe API Key", actorUserId);
        activeSecret.setId(secretId);

        deletedSecret = new Secret(environmentId, "OLD_KEY", "Old Key", actorUserId);
        deletedSecret.setId(secretId);
        deletedSecret.setStatus(SecretStatus.DELETED);

        targetUser = new User("alice@example.com", "hash", "Alice Smith");
        targetUser.setId(targetUserId);
    }

    @Test
    @DisplayName("Create Grant: WORKSPACE scope success")
    void testCreateWorkspaceScopeGrantSuccess() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.WORKSPACE, null, null, null, AccessPermission.SECRET_READ
        );

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

        AccessGrant savedGrant = new AccessGrant(
                workspaceId, targetUserId, AccessScope.WORKSPACE, null, null, null, AccessPermission.SECRET_READ, actorUserId
        );
        when(accessGrantRepository.saveAndFlush(any(AccessGrant.class))).thenReturn(savedGrant);

        AccessGrantResponse response = accessGrantService.createGrant(workspaceId, request, actorUserId);

        assertNotNull(response);
        assertEquals(AccessScope.WORKSPACE, response.scopeType());
        assertEquals(AccessPermission.SECRET_READ, response.permission());
        verify(effectiveAccessService).checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, actorUserId);
        verify(auditService).logSuccess(eq(AuditAction.ACCESS_GRANT_CREATED), any(), any(), eq(actorUserId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Create Grant: SECRET scope success with valid hierarchy")
    void testCreateSecretScopeGrantSuccess() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL
        );

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(prodEnvironment));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(activeSecret));

        AccessGrant savedGrant = new AccessGrant(
                workspaceId, targetUserId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, actorUserId
        );
        when(accessGrantRepository.saveAndFlush(any(AccessGrant.class))).thenReturn(savedGrant);

        AccessGrantResponse response = accessGrantService.createGrant(workspaceId, request, actorUserId);

        assertNotNull(response);
        assertEquals(AccessScope.SECRET, response.scopeType());
        assertEquals(AccessPermission.SECRET_REVEAL, response.permission());
        verify(effectiveAccessService).checkPermission(workspaceId, projectId, environmentId, secretId, AccessPermission.ACCESS_MANAGE, actorUserId);
    }

    @Test
    @DisplayName("Validation: Incompatible scope/permission rejected (environment.promote on SECRET scope)")
    void testIncompatiblePermissionScopeRejected() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.ENVIRONMENT_PROMOTE
        );

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(prodEnvironment));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(activeSecret));

        ApiException ex = assertThrows(ApiException.class, () ->
                accessGrantService.createGrant(workspaceId, request, actorUserId));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("environment-level operation"));
    }

    @Test
    @DisplayName("Validation: Branch permission on Production environment rejected")
    void testBranchPermissionOnProdRejected() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.ENVIRONMENT, projectId, environmentId, null, AccessPermission.SECRET_BRANCH
        );

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(prodEnvironment));

        ApiException ex = assertThrows(ApiException.class, () ->
                accessGrantService.createGrant(workspaceId, request, actorUserId));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("Feature branches are only permitted in DEVELOPMENT"));
    }

    @Test
    @DisplayName("Validation: Cannot grant access on deleted secret")
    void testGrantOnDeletedSecretRejected() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_READ
        );

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(devEnvironment));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(deletedSecret));

        ApiException ex = assertThrows(ApiException.class, () ->
                accessGrantService.createGrant(workspaceId, request, actorUserId));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("Cannot create access grants on a deleted secret"));
    }

    @Test
    @DisplayName("Anti-Privilege Escalation: Granter must possess administrative permission being granted")
    void testAntiPrivilegeEscalationGuard() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.PROJECT, projectId, null, null, AccessPermission.ACCESS_MANAGE
        );

        doThrow(ApiException.forbidden("Insufficient authority to grant administrative privilege"))
                .when(effectiveAccessService).checkPermission(workspaceId, projectId, null, null, AccessPermission.ACCESS_MANAGE, actorUserId);

        ApiException ex = assertThrows(ApiException.class, () ->
                accessGrantService.createGrant(workspaceId, request, actorUserId));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Duplicate Detection: Duplicate grant returns 409 Conflict")
    void testDuplicateGrantConflict() {
        CreateAccessGrantRequest request = new CreateAccessGrantRequest(
                targetUserId, AccessScope.WORKSPACE, null, null, null, AccessPermission.SECRET_READ
        );

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(accessGrantRepository.existsByWorkspaceIdAndUserIdAndScopeTypeAndProjectIdAndEnvironmentIdAndSecretIdAndPermission(
                workspaceId, targetUserId, AccessScope.WORKSPACE, null, null, null, AccessPermission.SECRET_READ
        )).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                accessGrantService.createGrant(workspaceId, request, actorUserId));

        assertEquals("RESOURCE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("Detail Endpoint: Successfully retrieves grant by ID")
    void testGetGrantByIdSuccess() {
        UUID grantId = UUID.randomUUID();
        AccessGrant grant = new AccessGrant(
                workspaceId, targetUserId, AccessScope.WORKSPACE, null, null, null, AccessPermission.SECRET_READ, actorUserId
        );
        grant.setId(grantId);

        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(true);
        when(accessGrantRepository.findByIdAndWorkspaceId(grantId, workspaceId)).thenReturn(Optional.of(grant));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

        AccessGrantResponse response = accessGrantService.getGrantById(workspaceId, grantId, actorUserId);

        assertNotNull(response);
        assertEquals(grantId, response.id());
        assertEquals("alice@example.com", response.userEmail());
    }

    @Test
    @DisplayName("List Endpoint: Filtered and paginated listing")
    void testListGrantsPaginated() {
        AccessGrant grant = new AccessGrant(
                workspaceId, targetUserId, AccessScope.WORKSPACE, null, null, null, AccessPermission.SECRET_READ, actorUserId
        );
        grant.setId(UUID.randomUUID());

        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(true);
        when(accessGrantRepository.findFilteredGrants(
                eq(workspaceId), eq(targetUserId), eq(AccessScope.WORKSPACE), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(grant), PageRequest.of(0, 20), 1));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

        PageResponse<AccessGrantResponse> response = accessGrantService.listGrants(
                workspaceId, targetUserId, AccessScope.WORKSPACE, null, null, null, null, PageRequest.of(0, 20), actorUserId
        );

        assertNotNull(response);
        assertEquals(1, response.totalElements());
        assertEquals(1, response.content().size());
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
        verify(auditService).logSuccess(eq(AuditAction.ACCESS_GRANT_REVOKED), any(), eq(grantId), eq(actorUserId), eq(workspaceId), any());
    }
}
