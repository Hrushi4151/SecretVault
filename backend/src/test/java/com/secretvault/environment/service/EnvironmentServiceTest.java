package com.secretvault.environment.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.dto.CreateEnvironmentRequest;
import com.secretvault.environment.dto.EnvironmentResponse;
import com.secretvault.environment.dto.UpdateEnvironmentRequest;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
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
class EnvironmentServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @InjectMocks
    private EnvironmentService environmentService;

    private UUID userId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private Project testProject;
    private Environment testEnv;
    private WorkspaceMembership ownerMembership;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();

        testProject = new Project(workspaceId, "Core API", "core-api", "API", userId);
        testProject.setId(projectId);

        testEnv = new Environment(projectId, "Production", "production", EnvType.PRODUCTION, "Prod Tier", true, userId);
        testEnv.setId(environmentId);

        ownerMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("Should list environments belonging to a project")
    void testGetEnvironments() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(testProject));
        when(environmentRepository.findByProjectId(projectId))
                .thenReturn(List.of(testEnv));

        List<EnvironmentResponse> responses = environmentService.getEnvironments(workspaceId, projectId, userId);

        assertEquals(1, responses.size());
        assertEquals("Production", responses.getFirst().name());
        assertTrue(responses.getFirst().isProtected());
    }

    @Test
    @DisplayName("Should fetch environment by ID within valid project hierarchy")
    void testGetEnvironmentByIdSuccess() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(testProject));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId))
                .thenReturn(Optional.of(testEnv));

        EnvironmentResponse response = environmentService.getEnvironmentById(workspaceId, projectId, environmentId, userId);

        assertNotNull(response);
        assertEquals("production", response.slug());
    }

    @Test
    @DisplayName("Should throw 404 when project does not belong to workspace (Hierarchy Isolation)")
    void testGetEnvironmentHierarchyMismatch() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                environmentService.getEnvironmentById(workspaceId, projectId, environmentId, userId));
        assertEquals("RESOURCE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("Should create custom environment for OWNER/ADMIN")
    void testCreateEnvironmentSuccess() {
        CreateEnvironmentRequest request = new CreateEnvironmentRequest(
                "QA Testing", "qa-testing", EnvType.STAGING, "QA testing cluster", false
        );

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(testProject));
        when(environmentRepository.existsByProjectIdAndSlug(projectId, "qa-testing")).thenReturn(false);
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        EnvironmentResponse response = environmentService.createEnvironment(workspaceId, projectId, request, userId);

        assertNotNull(response);
        assertEquals("QA Testing", response.name());
        assertEquals("qa-testing", response.slug());
        assertEquals(EnvType.STAGING, response.envType());
    }

    @Test
    @DisplayName("Should forbid custom environment creation for DEVELOPER role")
    void testCreateEnvironmentForbiddenForDeveloper() {
        WorkspaceMembership devMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        CreateEnvironmentRequest request = new CreateEnvironmentRequest(
                "Sandbox", "sandbox", EnvType.DEVELOPMENT, "Dev sandbox", false
        );

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(devMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(testProject));

        ApiException ex = assertThrows(ApiException.class, () ->
                environmentService.createEnvironment(workspaceId, projectId, request, userId));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should update environment metadata and protection tier")
    void testUpdateEnvironmentSuccess() {
        UpdateEnvironmentRequest request = new UpdateEnvironmentRequest(
                "Production Cluster A", "High-availability cluster", EnvType.PRODUCTION, true, null
        );

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(testProject));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId))
                .thenReturn(Optional.of(testEnv));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnvironmentResponse response = environmentService.updateEnvironment(workspaceId, projectId, environmentId, request, userId);

        assertEquals("Production Cluster A", response.name());
        assertTrue(response.isProtected());
    }

    @Test
    @DisplayName("Should delete environment when caller is OWNER")
    void testDeleteEnvironmentSuccess() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(testProject));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId))
                .thenReturn(Optional.of(testEnv));

        environmentService.deleteEnvironment(workspaceId, projectId, environmentId, userId);

        verify(environmentRepository).delete(testEnv);
    }
}
