package com.secretvault.repository.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.repository.dto.RepositoryDtos.ConnectRepositoryRequest;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.service.RepositoryService;
import com.secretvault.repository.service.RepositoryService.RepositorySummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/repositories")
@Tag(name = "Repository Management", description = "Endpoints for registering and managing code repositories for secret leak scanning")
@SecurityRequirement(name = "BearerAuth")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GetMapping
    @Operation(summary = "List repositories registered in a workspace")
    public ResponseEntity<ApiResponse<Page<RepositorySummaryDto>>> listRepositories(
            @PathVariable UUID workspaceId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<RepositorySummaryDto> result = repositoryService.listRepositories(workspaceId, actorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{repositoryId}")
    @Operation(summary = "Get single repository metadata and leak detection status")
    public ResponseEntity<ApiResponse<RepositorySummaryDto>> getRepository(
            @PathVariable UUID workspaceId,
            @PathVariable UUID repositoryId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RepositorySummaryDto result = repositoryService.getRepository(workspaceId, repositoryId, actorId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    @Operation(summary = "Connect a code repository for continuous secret scanning")
    public ResponseEntity<ApiResponse<RepositoryEntity>> connectRepository(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody ConnectRepositoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RepositoryEntity created = repositoryService.connectRepository(
                workspaceId,
                request.provider(),
                request.externalId(),
                request.owner(),
                request.name(),
                request.defaultBranch(),
                request.cloneUrl(),
                request.visibility(),
                actorId
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Repository connected successfully"));
    }

    @DeleteMapping("/{repositoryId}")
    @Operation(summary = "Disconnect/archive a repository from SecretVault")
    public ResponseEntity<ApiResponse<Void>> disconnectRepository(
            @PathVariable UUID workspaceId,
            @PathVariable UUID repositoryId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        repositoryService.disconnectRepository(workspaceId, repositoryId, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Repository disconnected successfully"));
    }
}
