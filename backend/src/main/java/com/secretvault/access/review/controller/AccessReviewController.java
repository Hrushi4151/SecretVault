package com.secretvault.access.review.controller;

import com.secretvault.access.review.dto.*;
import com.secretvault.access.review.service.AccessReviewService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/access-reviews")
public class AccessReviewController {

    private final AccessReviewService reviewService;

    public AccessReviewController(AccessReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccessReviewCampaignResponse>>> listCampaigns(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<AccessReviewCampaignResponse> campaigns = reviewService.listCampaigns(workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(campaigns));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccessReviewCampaignResponse>> createCampaign(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AccessReviewCampaignResponse response = reviewService.createCampaign(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Access review campaign created successfully"));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<AccessReviewCampaignResponse>> getCampaign(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AccessReviewCampaignResponse response = reviewService.getCampaign(workspaceId, campaignId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{campaignId}/items")
    public ResponseEntity<ApiResponse<List<AccessReviewItemResponse>>> listCampaignItems(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<AccessReviewItemResponse> items = reviewService.listCampaignItems(workspaceId, campaignId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PostMapping("/{campaignId}/items/{itemId}/decide")
    public ResponseEntity<ApiResponse<AccessReviewItemResponse>> decideItem(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @PathVariable UUID itemId,
            @Valid @RequestBody DecideReviewItemRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AccessReviewItemResponse response = reviewService.decideItem(workspaceId, campaignId, itemId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Decision recorded successfully"));
    }

    @PostMapping("/{campaignId}/complete")
    public ResponseEntity<ApiResponse<CampaignAttestationReportResponse>> completeCampaign(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CampaignAttestationReportResponse response = reviewService.completeCampaign(workspaceId, campaignId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Access review campaign completed and attestation ledger generated"));
    }

    @GetMapping("/{campaignId}/attestation")
    public ResponseEntity<ApiResponse<CampaignAttestationReportResponse>> getAttestation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CampaignAttestationReportResponse response = reviewService.getAttestationReport(workspaceId, campaignId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
