package com.secretvault.machine.controller;

import com.secretvault.access.dto.EffectiveAccessExplanation;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.machine.dto.MachineDtos;
import com.secretvault.machine.service.MachineIdentityService;
import com.secretvault.machine.service.MachinePermissionService;
import com.secretvault.machine.service.MachineSessionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/workspaces/{workspaceId}/machine-identities", "/api/v1/workspaces/{workspaceId}/machines"})
public class MachineIdentityController {

    private final MachineIdentityService machineService;
    private final MachinePermissionService permissionService;
    private final MachineSessionService sessionService;
    private final EffectiveAccessService effectiveAccessService;

    public MachineIdentityController(
            MachineIdentityService machineService,
            MachinePermissionService permissionService,
            MachineSessionService sessionService,
            EffectiveAccessService effectiveAccessService
    ) {
        this.machineService = machineService;
        this.permissionService = permissionService;
        this.sessionService = sessionService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MachineDtos.MachineIdentityResponse>> createMachineIdentity(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody MachineDtos.CreateMachineIdentityRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        MachineDtos.MachineIdentityResponse response = machineService.createMachineIdentity(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Machine identity created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MachineDtos.MachineIdentityResponse>>> listMachineIdentities(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<MachineDtos.MachineIdentityResponse> list = machineService.listMachineIdentities(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MachineDtos.MachineIdentityResponse>> getMachineIdentity(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        MachineDtos.MachineIdentityResponse response = machineService.getMachineIdentity(id, workspaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<MachineDtos.MachineIdentityResponse>> updateMachineIdentity(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @Valid @RequestBody MachineDtos.UpdateMachineIdentityRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        MachineDtos.MachineIdentityResponse response = machineService.updateMachineIdentity(id, workspaceId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Machine identity updated successfully"));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<MachineDtos.MachineIdentityResponse>> disableMachineIdentity(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        MachineDtos.MachineIdentityResponse response = machineService.disableMachineIdentity(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Machine identity disabled successfully"));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<MachineDtos.MachineIdentityResponse>> enableMachineIdentity(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        MachineDtos.MachineIdentityResponse response = machineService.enableMachineIdentity(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Machine identity enabled successfully"));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<MachineDtos.MachineIdentityResponse>> revokeMachineIdentity(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        MachineDtos.MachineIdentityResponse response = machineService.revokeMachineIdentity(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Machine identity permanently revoked"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMachineIdentity(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        machineService.deleteMachineIdentity(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Machine identity deleted successfully"));
    }

    // --- Granular Permissions Management ---

    @GetMapping({"/{id}/permissions", "/{id}/grants"})
    public ResponseEntity<ApiResponse<List<MachineDtos.MachineGrantResponse>>> listPermissions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<MachineDtos.MachineGrantResponse> grants = permissionService.listGrants(workspaceId, id);
        return ResponseEntity.ok(ApiResponse.success(grants));
    }

    @PostMapping({"/{id}/permissions", "/{id}/grants"})
    public ResponseEntity<ApiResponse<MachineDtos.MachineGrantResponse>> addPermission(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @Valid @RequestBody MachineDtos.CreateMachineGrantRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, request.projectId(), request.environmentId(), request.secretId(), AccessPermission.ACCESS_MANAGE, principal.getId());
        MachineDtos.MachineGrantResponse grant = permissionService.addGrant(workspaceId, id, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(grant, "Machine grant added successfully"));
    }

    @DeleteMapping({"/{id}/permissions/{grantId}", "/{id}/grants/{grantId}"})
    public ResponseEntity<ApiResponse<Void>> removePermission(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @PathVariable UUID grantId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        permissionService.removeGrant(workspaceId, id, grantId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Machine grant removed successfully"));
    }

    // --- Session Governance ---

    @GetMapping("/{id}/sessions")
    public ResponseEntity<ApiResponse<List<MachineDtos.MachineSessionResponse>>> listSessions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<MachineDtos.MachineSessionResponse> sessions = sessionService.listSessions(workspaceId, id);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @PostMapping("/{id}/sessions/{sessionId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        sessionService.revokeSession(workspaceId, id, sessionId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Machine session revoked successfully"));
    }

    @PostMapping("/{id}/sessions/revoke-all")
    public ResponseEntity<ApiResponse<Integer>> revokeAllSessions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        int revokedCount = sessionService.revokeAllSessions(workspaceId, id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(revokedCount, "All active machine sessions revoked successfully"));
    }

    // --- WhyAccess Inspection ---

    @GetMapping("/{id}/why-access")
    public ResponseEntity<ApiResponse<List<EffectiveAccessExplanation>>> explainMachineAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) UUID secretId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<EffectiveAccessExplanation> explanations = effectiveAccessService.explainAccess(
                workspaceId, projectId, environmentId, secretId, id
        );
        return ResponseEntity.ok(ApiResponse.success(explanations));
    }
}
