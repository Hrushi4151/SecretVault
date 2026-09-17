package com.secretvault.project.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.project.dto.ProjectResponse;
import com.secretvault.project.dto.UpdateProjectRequest;
import com.secretvault.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for managing Projects within a Workspace.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Projects", description = "Endpoints for managing application projects and their default environments")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @Operation(summary = "List Workspace Projects", description = "Retrieves all projects within the given workspace.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Projects retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a member of the workspace"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace not found")
    })
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listProjects(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ProjectResponse> projects = projectService.getProjects(workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @PostMapping
    @Operation(summary = "Create Project", description = "Creates a new project and automatically seeds default environments (dev, staging, prod).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Project created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate project slug")
    })
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.createProject(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(project));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get Project Details", description = "Retrieves project metadata and its environment summaries.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a member of the workspace"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found in workspace")
    })
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProjectResponse project = projectService.getProjectById(workspaceId, projectId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Update Project", description = "Updates project name, description, or lifecycle status. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found in workspace")
    })
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProjectRequest request) {
        ProjectResponse project = projectService.updateProject(workspaceId, projectId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete Project", description = "Deletes a project and all its environments. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found in workspace")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        projectService.deleteProject(workspaceId, projectId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Project successfully deleted", "projectId", projectId)));
    }
}
