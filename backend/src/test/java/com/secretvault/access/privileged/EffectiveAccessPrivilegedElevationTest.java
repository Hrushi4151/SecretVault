package com.secretvault.access.privileged;

import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.privileged.entity.PrivilegedAccessElevation;
import com.secretvault.access.privileged.model.ElevationStatus;
import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.access.privileged.repository.PrivilegedAccessElevationRepository;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectiveAccessPrivilegedElevationTest {

    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMembershipRepository membershipRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private SecretRepository secretRepository;
    @Mock private ProjectAccessRepository projectAccessRepository;
    @Mock private EnvironmentAccessRepository environmentAccessRepository;
    @Mock private AccessGrantRepository accessGrantRepository;
    @Mock private JitAccessRequestRepository jitRepository;
    @Mock private PrivilegedAccessElevationRepository privilegedElevationRepository;

    private Clock fixedClock;
    private Instant now;
    private EffectiveAccessService effectiveAccessService;

    private UUID workspaceId;
    private UUID userId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;

    private Project project;
    private Environment environment;
    private Secret secret;
    private WorkspaceMembership membership;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-03T12:00:00Z");
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();

        project = new Project(workspaceId, "Banking Core", "banking-core", "Financial core", userId);
        project.setId(projectId);

        environment = new Environment(projectId, "Production", "production", EnvType.PRODUCTION, "Prod env", true, userId);
        environment.setId(environmentId);

        secret = new Secret(environmentId, "DB_PASSWORD", "Database password", userId);
        secret.setId(secretId);
        secret.setStatus(SecretStatus.ACTIVE);

        membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.VIEWER);

        effectiveAccessService = new EffectiveAccessService(
                workspaceRepository, membershipRepository, projectRepository, environmentRepository,
                secretRepository, projectAccessRepository, environmentAccessRepository,
                accessGrantRepository, jitRepository, fixedClock, null, null, privilegedElevationRepository
        );
    }

    @Test
    @DisplayName("Active Privileged Elevation authorizes secret reveal for VIEWER role")
    void activeElevationAuthorizesReveal() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(environment));

        PrivilegedAccessElevation elev = new PrivilegedAccessElevation(
                workspaceId, UUID.randomUUID(), userId,
                PrivilegedAction.SECRET_REVEAL, PrivilegedPolicyScope.ENVIRONMENT,
                projectId, environmentId, null,
                AccessPermission.SECRET_REVEAL, false,
                now.minusSeconds(60), now.plus(Duration.ofMinutes(30))
        );

        when(privilegedElevationRepository.findActiveElevationsForUserAndPermission(
                eq(workspaceId), eq(userId), eq(AccessPermission.SECRET_REVEAL), any(Instant.class)
        )).thenReturn(List.of(elev));

        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, userId
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.sourceType()).isEqualTo(AccessSourceType.PRIVILEGED_ELEVATION);
        assertThat(decision.reason()).contains("Active temporary privileged elevation");
    }

    @Test
    @DisplayName("Active Break-Glass Emergency Elevation authorizes production secret reveal with BREAK_GLASS source")
    void activeBreakGlassAuthorizesReveal() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(environment));

        PrivilegedAccessElevation elev = new PrivilegedAccessElevation(
                workspaceId, UUID.randomUUID(), userId,
                PrivilegedAction.BREAK_GLASS_REQUEST, PrivilegedPolicyScope.ENVIRONMENT,
                projectId, environmentId, null,
                AccessPermission.SECRET_REVEAL, true,
                now.minusSeconds(60), now.plus(Duration.ofMinutes(30))
        );

        when(privilegedElevationRepository.findActiveElevationsForUserAndPermission(
                eq(workspaceId), eq(userId), eq(AccessPermission.SECRET_REVEAL), any(Instant.class)
        )).thenReturn(List.of(elev));

        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, userId
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.sourceType()).isEqualTo(AccessSourceType.BREAK_GLASS);
        assertThat(decision.reason()).contains("Active Break-Glass emergency access");
    }

    @Test
    @DisplayName("Expired Privileged Elevation is denied in real time")
    void expiredElevationDeniedRealTime() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(environment));

        PrivilegedAccessElevation elev = new PrivilegedAccessElevation(
                workspaceId, UUID.randomUUID(), userId,
                PrivilegedAction.SECRET_REVEAL, PrivilegedPolicyScope.ENVIRONMENT,
                projectId, environmentId, null,
                AccessPermission.SECRET_REVEAL, false,
                now.minusSeconds(3600), now.minusSeconds(1) // expired
        );

        when(privilegedElevationRepository.findActiveElevationsForUserAndPermission(
                eq(workspaceId), eq(userId), eq(AccessPermission.SECRET_REVEAL), any(Instant.class)
        )).thenReturn(List.of(elev));

        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceId, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, userId
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.deniedReason()).contains("VIEWER role is strictly forbidden from revealing secret values");
    }
}
