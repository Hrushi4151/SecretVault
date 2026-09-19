package com.secretvault.access.review.controller;

import com.secretvault.access.review.dto.*;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.entity.ReviewDecision;
import com.secretvault.access.review.service.AccessReviewService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/workspaces/{workspaceId}/access-reviews", "/api/v1/workspaces/{workspaceId}/access/reviews"})
public class AccessReviewController {

    private final AccessReviewService reviewService;

    public AccessReviewController(AccessReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AccessReviewCampaignResponse>>> listCampaigns(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) CampaignStatus status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageResponse<AccessReviewCampaignResponse> campaigns = reviewService.listCampaignsPaginated(
                workspaceId, status, pageable, principal.getId()
        );
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
    public ResponseEntity<ApiResponse<PageResponse<AccessReviewItemResponse>>> listCampaignItems(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @RequestParam(required = false) ReviewDecision decision,
            @RequestParam(required = false) UUID userId,
            @PageableDefault(size = 50) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageResponse<AccessReviewItemResponse> items = reviewService.listCampaignItemsPaginated(
                workspaceId, campaignId, decision, userId, pageable, principal.getId()
        );
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

    @PostMapping("/{campaignId}/items/{itemId}/certify")
    public ResponseEntity<ApiResponse<AccessReviewItemResponse>> certifyItem(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @PathVariable UUID itemId,
            @RequestBody(required = false) DecideReviewItemRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String reason = request != null ? request.decisionReason() : "Certified during periodic access review";
        DecideReviewItemRequest req = new DecideReviewItemRequest(ReviewDecision.KEEP, reason);
        AccessReviewItemResponse response = reviewService.decideItem(workspaceId, campaignId, itemId, req, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Access certified successfully"));
    }

    @PostMapping("/{campaignId}/items/{itemId}/revoke")
    public ResponseEntity<ApiResponse<AccessReviewItemResponse>> revokeItem(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @PathVariable UUID itemId,
            @RequestBody(required = false) DecideReviewItemRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String reason = request != null ? request.decisionReason() : "Access revoked during periodic access review";
        DecideReviewItemRequest req = new DecideReviewItemRequest(ReviewDecision.REVOKE, reason);
        AccessReviewItemResponse response = reviewService.decideItem(workspaceId, campaignId, itemId, req, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Access revoked successfully"));
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

    @PostMapping("/{campaignId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelCampaign(
            @PathVariable UUID workspaceId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        reviewService.cancelCampaign(workspaceId, campaignId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Access review campaign cancelled"));
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
