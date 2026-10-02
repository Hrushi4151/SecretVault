package com.secretvault.provider.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.dto.PagedResponse;
import com.secretvault.provider.dto.CreateProviderIntegrationRequest;
import com.secretvault.provider.dto.CreateResourceMappingRequest;
import com.secretvault.provider.dto.ProviderIntegrationResponse;
import com.secretvault.provider.dto.ProviderResourceMappingResponse;
import com.secretvault.provider.dto.UpdateProviderIntegrationRequest;
import com.secretvault.provider.dto.UpdateResourceMappingRequest;
import com.secretvault.provider.dto.ValidateIntegrationResponse;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderCapability;
import com.secretvault.provider.model.ProviderDiscoveredEnvironment;
import com.secretvault.provider.model.ProviderDiscoveredResource;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.service.ProviderIntegrationService;
import com.secretvault.provider.service.ProviderResourceMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/integrations")
@Tag(name = "Provider Integrations", description = "Management of external platform provider connections and resource mappings")
public class ProviderIntegrationController {

    private final ProviderIntegrationService integrationService;
    private final ProviderResourceMappingService mappingService;

    public ProviderIntegrationController(
            ProviderIntegrationService integrationService,
            ProviderResourceMappingService mappingService
    ) {
        this.integrationService = integrationService;
        this.mappingService = mappingService;
    }

    @PostMapping
    @Operation(summary = "Create a new provider integration with envelope-encrypted credentials")
    public ResponseEntity<ApiResponse<ProviderIntegrationResponse>> createIntegration(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateProviderIntegrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProviderIntegrationResponse response = integrationService.createIntegration(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List provider integrations within a workspace")
    public ResponseEntity<ApiResponse<PagedResponse<ProviderIntegrationResponse>>> listIntegrations(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) IntegrationStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<ProviderIntegrationResponse> result = integrationService.listIntegrations(
                workspaceId, providerType, status, search, page, size, sort, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @GetMapping("/{integrationId}")
    @Operation(summary = "Get provider integration details with redacted credentials")
    public ResponseEntity<ApiResponse<ProviderIntegrationResponse>> getIntegration(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProviderIntegrationResponse response = integrationService.getIntegration(workspaceId, integrationId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{integrationId}")
    @Operation(summary = "Update provider integration settings or perform atomic credential rotation")
    public ResponseEntity<ApiResponse<ProviderIntegrationResponse>> updateIntegration(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @Valid @RequestBody UpdateProviderIntegrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProviderIntegrationResponse response = integrationService.updateIntegration(workspaceId, integrationId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{integrationId}")
    @Operation(summary = "Delete a provider integration and all mapped resources")
    public ResponseEntity<ApiResponse<Void>> deleteIntegration(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        integrationService.deleteIntegration(workspaceId, integrationId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{integrationId}/validate")
    @Operation(summary = "Validate connection credentials against the live external provider")
    public ResponseEntity<ApiResponse<ValidateIntegrationResponse>> validateIntegration(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ValidateIntegrationResponse response = integrationService.validateIntegration(workspaceId, integrationId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{integrationId}/capabilities")
    @Operation(summary = "Get capabilities supported by this provider integration")
    public ResponseEntity<ApiResponse<Set<ProviderCapability>>> getCapabilities(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Set<ProviderCapability> capabilities = integrationService.getCapabilities(workspaceId, integrationId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(capabilities));
    }

    @GetMapping("/{integrationId}/resources")
    @Operation(summary = "Discover projects or services accessible on the external provider")
    public ResponseEntity<ApiResponse<List<ProviderDiscoveredResource>>> discoverResources(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ProviderDiscoveredResource> resources = integrationService.discoverResources(workspaceId, integrationId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(resources));
    }

    @GetMapping("/{integrationId}/resources/{providerResourceId}/environments")
    @Operation(summary = "Discover deployment environments for a specific provider resource")
    public ResponseEntity<ApiResponse<List<ProviderDiscoveredEnvironment>>> discoverEnvironments(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @PathVariable String providerResourceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ProviderDiscoveredEnvironment> envs = integrationService.discoverEnvironments(
                workspaceId, integrationId, providerResourceId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(envs));
    }

    @PostMapping("/{integrationId}/mappings")
    @Operation(summary = "Create an explicit project and environment resource mapping")
    public ResponseEntity<ApiResponse<ProviderResourceMappingResponse>> createMapping(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @Valid @RequestBody CreateResourceMappingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProviderResourceMappingResponse response = mappingService.createMapping(workspaceId, integrationId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{integrationId}/mappings")
    @Operation(summary = "List all resource mappings for a provider integration")
    public ResponseEntity<ApiResponse<List<ProviderResourceMappingResponse>>> listMappings(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ProviderResourceMappingResponse> response = mappingService.listMappings(workspaceId, integrationId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{integrationId}/mappings/{mappingId}")
    @Operation(summary = "Update an existing resource mapping")
    public ResponseEntity<ApiResponse<ProviderResourceMappingResponse>> updateMapping(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @PathVariable UUID mappingId,
            @Valid @RequestBody UpdateResourceMappingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProviderResourceMappingResponse response = mappingService.updateMapping(workspaceId, integrationId, mappingId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{integrationId}/mappings/{mappingId}")
    @Operation(summary = "Delete a resource mapping")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(
            @PathVariable UUID workspaceId,
            @PathVariable UUID integrationId,
            @PathVariable UUID mappingId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        mappingService.deleteMapping(workspaceId, integrationId, mappingId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
