package com.secretvault.repository.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.repository.dto.RepositoryDtos.AllowlistFindingRequest;
import com.secretvault.repository.dto.RepositoryDtos.UpdateFindingStatusRequest;
import com.secretvault.repository.entity.FindingAllowlist;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.entity.SecretFindingOccurrence;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.RepoFindingStatus;
import com.secretvault.repository.service.SecretFindingService;
import com.secretvault.repository.service.SecretFindingService.FindingStatsDto;
import com.secretvault.repository.service.SecretFindingService.WhyExposedExplanation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/secret-findings")
@Tag(name = "Secret Finding Management", description = "Endpoints for triaging, investigating, and explaining exposed secret findings with zero-plaintext leakage")
@SecurityRequirement(name = "BearerAuth")
public class SecretFindingController {

    private final SecretFindingService findingService;

    public SecretFindingController(SecretFindingService findingService) {
        this.findingService = findingService;
    }

    @GetMapping
    @Operation(summary = "List exposed secret findings with filtering and search")
    public ResponseEntity<ApiResponse<Page<SecretFinding>>> listFindings(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) RepoFindingSeverity severity,
            @RequestParam(required = false) RepoFindingStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<SecretFinding> findings = findingService.listFindings(
                workspaceId, repositoryId, severity, status, search, actorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(findings));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get workspace-wide secret leak statistics and posture counts")
    public ResponseEntity<ApiResponse<FindingStatsDto>> getStats(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        FindingStatsDto stats = findingService.getWorkspaceStats(workspaceId, actorId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/{findingId}")
    @Operation(summary = "Get finding details with masked evidence and metadata")
    public ResponseEntity<ApiResponse<SecretFinding>> getFinding(
            @PathVariable UUID workspaceId,
            @PathVariable UUID findingId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecretFinding finding = findingService.getFinding(workspaceId, findingId, actorId);
        return ResponseEntity.ok(ApiResponse.success(finding));
    }

    @GetMapping("/{findingId}/occurrences")
    @Operation(summary = "List historical and branch occurrences for this secret finding")
    public ResponseEntity<ApiResponse<Page<SecretFindingOccurrence>>> getOccurrences(
            @PathVariable UUID workspaceId,
            @PathVariable UUID findingId,
            @PageableDefault(size = 20, sort = "firstSeen", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<SecretFindingOccurrence> occurrences = findingService.getOccurrences(
                workspaceId, findingId, actorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(occurrences));
    }

    @GetMapping("/{findingId}/why-exposed")
    @Operation(summary = "Explain why this secret is considered exposed (WhyExposed / Explainability)")
    public ResponseEntity<ApiResponse<WhyExposedExplanation>> whyExposed(
            @PathVariable UUID workspaceId,
            @PathVariable UUID findingId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        WhyExposedExplanation explanation = findingService.generateWhyExposed(workspaceId, findingId, actorId);
        return ResponseEntity.ok(ApiResponse.success(explanation));
    }

    @PatchMapping("/{findingId}/status")
    @Operation(summary = "Update finding status (CONFIRMED, FALSE_POSITIVE, IGNORED, REOPENED)")
    public ResponseEntity<ApiResponse<SecretFinding>> updateStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID findingId,
            @Valid @RequestBody UpdateFindingStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecretFinding updated = findingService.updateStatus(
                workspaceId, findingId, request.status(), request.reason(), actorId);
        return ResponseEntity.ok(ApiResponse.success(updated, "Finding status updated to " + request.status()));
    }

    @PostMapping("/allowlist")
    @Operation(summary = "Create an allowlist rule to safely ignore false positives or accepted test tokens")
    public ResponseEntity<ApiResponse<FindingAllowlist>> createAllowlist(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AllowlistFindingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        FindingAllowlist allowlist = findingService.allowlistFinding(
                workspaceId,
                request.repositoryId(),
                request.fingerprint(),
                request.detector(),
                request.path(),
                request.reason(),
                request.expiresAt(),
                actorId
        );
        return ResponseEntity.ok(ApiResponse.success(allowlist, "Allowlist entry created successfully"));
    }
}
