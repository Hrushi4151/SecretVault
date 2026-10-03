package com.secretvault.repository.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.repository.dto.RepositoryDtos.RemediateFindingRequest;
import com.secretvault.repository.entity.FindingRemediationJob;
import com.secretvault.repository.service.FindingRemediationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/repository-remediations")
@Tag(name = "Secret Finding Remediation", description = "Endpoints for remediating exposed credentials via rotation, revocation, or false positive resolution")
@SecurityRequirement(name = "BearerAuth")
public class RepositoryRemediationController {

    private final FindingRemediationService remediationService;

    public RepositoryRemediationController(FindingRemediationService remediationService) {
        this.remediationService = remediationService;
    }

    @PostMapping("/{findingId}")
    @Operation(summary = "Execute remediation action (ROTATE_SECRET, REVOKE_SECRET, MARK_FALSE_POSITIVE, IGNORE)")
    public ResponseEntity<ApiResponse<FindingRemediationJob>> remediateFinding(
            @PathVariable UUID workspaceId,
            @PathVariable UUID findingId,
            @Valid @RequestBody RemediateFindingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        FindingRemediationJob job = remediationService.remediateFinding(
                workspaceId, findingId, request.action(), request.notes(), actorId);
        return ResponseEntity.ok(ApiResponse.success(job, "Remediation action " + request.action() + " executed"));
    }
}
