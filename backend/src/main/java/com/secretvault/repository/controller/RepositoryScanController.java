package com.secretvault.repository.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.repository.dto.RepositoryDtos.LocalScanRequest;
import com.secretvault.repository.dto.RepositoryDtos.TriggerScanRequest;
import com.secretvault.repository.entity.RepositoryScan;
import com.secretvault.repository.model.ScanType;
import com.secretvault.repository.service.RepositoryScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@Tag(name = "Repository Scanning", description = "Endpoints for initiating and tracking secret leak scans across repositories, Git histories, and archives")
@SecurityRequirement(name = "BearerAuth")
public class RepositoryScanController {

    private final RepositoryScanService scanService;

    public RepositoryScanController(RepositoryScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/repositories/{repositoryId}/scans")
    @Operation(summary = "Trigger a repository scan (full, incremental, git-history, or PR)")
    public ResponseEntity<ApiResponse<RepositoryScan>> triggerScan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID repositoryId,
            @RequestBody(required = false) TriggerScanRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        ScanType scanType = request != null && request.scanType() != null ? request.scanType() : ScanType.INCREMENTAL;
        String branch = request != null ? request.branch() : null;

        RepositoryScan scan = scanService.triggerRepositoryScan(workspaceId, repositoryId, scanType, branch, actorId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(scan, "Scan triggered successfully"));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/repository-scans/local")
    @Operation(summary = "Trigger a local workspace directory scan")
    public ResponseEntity<ApiResponse<RepositoryScan>> scanLocalDirectory(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID repositoryId,
            @Valid @RequestBody LocalScanRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RepositoryScan scan = scanService.executeLocalDirectoryScan(
                workspaceId, repositoryId, request.path(), request.scanHistory(), actorId);
        return ResponseEntity.ok(ApiResponse.success(scan, "Local directory scan completed"));
    }

    @PostMapping(value = "/api/v1/workspaces/{workspaceId}/repository-scans/archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and scan a source code archive (.zip) safely")
    public ResponseEntity<ApiResponse<RepositoryScan>> scanArchive(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        UUID actorId = principal != null ? principal.getId() : null;
        RepositoryScan scan = scanService.executeArchiveScan(
                workspaceId, repositoryId, file.getInputStream(), actorId);
        return ResponseEntity.ok(ApiResponse.success(scan, "Archive scan completed"));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/repository-scans")
    @Operation(summary = "List repository scans for a workspace")
    public ResponseEntity<ApiResponse<Page<RepositoryScan>>> listScans(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID repositoryId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<RepositoryScan> scans = scanService.listScans(workspaceId, repositoryId, actorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(scans));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/repository-scans/{scanId}")
    @Operation(summary = "Get scan details and progress")
    public ResponseEntity<ApiResponse<RepositoryScan>> getScan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID scanId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RepositoryScan scan = scanService.getScan(workspaceId, scanId, actorId);
        return ResponseEntity.ok(ApiResponse.success(scan));
    }
}
