package com.secretvault.project.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.project.dto.ProjectResponse;
import com.secretvault.project.dto.UpdateProjectRequest;
import com.secretvault.project.entity.Project;
import com.secretvault.project.entity.ProjectStatus;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @InjectMocks
    private ProjectService projectService;

    private UUID userId;
    private UUID workspaceId;
    private UUID projectId;
    private Project testProject;
    private WorkspaceMembership ownerMembership;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        testProject = new Project(workspaceId, "Payment Service", "payment-service", "Payment API", userId);
        testProject.setId(projectId);

        ownerMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("Should list projects in workspace for authorized member")
    void testGetProjects() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(testProject));
        when(environmentRepository.findByProjectId(projectId)).thenReturn(List.of(
                new Environment(projectId, "Development", "development", EnvType.DEVELOPMENT, "Dev", false, userId)
        ));

        List<ProjectResponse> responses = projectService.getProjects(workspaceId, userId);

        assertEquals(1, responses.size());
        assertEquals("Payment Service", responses.getFirst().name());
        assertEquals(1, responses.getFirst().environments().size());
    }

    @Test
    @DisplayName("Should create project and automatically seed default environments (dev, staging, prod)")
    void testCreateProjectSuccess() {
        CreateProjectRequest request = new CreateProjectRequest("Auth API", "auth-api", "Authentication gateway");

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.existsByWorkspaceIdAndSlug(workspaceId, "auth-api")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(environmentRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Environment> envs = inv.getArgument(0);
            envs.forEach(e -> e.setId(UUID.randomUUID()));
            return envs;
        });

        ProjectResponse response = projectService.createProject(workspaceId, request, userId);

        assertNotNull(response);
        assertEquals("Auth API", response.name());
        assertEquals("auth-api", response.slug());
        assertEquals(3, response.environments().size());
        assertTrue(response.environments().stream().anyMatch(e -> e.slug().equals("development") && e.envType() == EnvType.DEVELOPMENT));
        assertTrue(response.environments().stream().anyMatch(e -> e.slug().equals("staging") && e.envType() == EnvType.STAGING));
        assertTrue(response.environments().stream().anyMatch(e -> e.slug().equals("production") && e.envType() == EnvType.PRODUCTION && e.isProtected()));

        verify(environmentRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should forbid project creation for VIEWER role")
    void testCreateProjectForbiddenForViewer() {
        WorkspaceMembership viewerMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.VIEWER);
        CreateProjectRequest request = new CreateProjectRequest("Billing", "billing", "Billing microservice");

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(viewerMembership));

        ApiException ex = assertThrows(ApiException.class, () -> projectService.createProject(workspaceId, request, userId));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should reject project creation with duplicate slug in workspace")
    void testCreateProjectConflictOnDuplicateSlug() {
        CreateProjectRequest request = new CreateProjectRequest("Payment Service", "payment-service", "Payment API");

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.existsByWorkspaceIdAndSlug(workspaceId, "payment-service")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> projectService.createProject(workspaceId, request, userId));
        assertEquals("RESOURCE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("Should update project metadata when caller is OWNER/ADMIN")
    void testUpdateProjectSuccess() {
        UpdateProjectRequest request = new UpdateProjectRequest("Payment Gateway V2", "Updated description", ProjectStatus.ACTIVE);

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(testProject));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.updateProject(workspaceId, projectId, request, userId);

        assertEquals("Payment Gateway V2", response.name());
        assertEquals("Updated description", response.description());
    }

    @Test
    @DisplayName("Should forbid DEVELOPER role from updating project metadata")
    void testUpdateProjectForbiddenForDeveloper() {
        WorkspaceMembership devMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        UpdateProjectRequest request = new UpdateProjectRequest("Renamed", null, null);

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(devMembership));

        ApiException ex = assertThrows(ApiException.class, () -> projectService.updateProject(workspaceId, projectId, request, userId));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should delete project when caller is OWNER")
    void testDeleteProjectSuccess() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(testProject));

        projectService.deleteProject(workspaceId, projectId, userId);

        verify(projectRepository).delete(testProject);
    }
}
