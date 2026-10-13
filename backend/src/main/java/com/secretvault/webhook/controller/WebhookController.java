package com.secretvault.webhook.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.webhook.entity.WebhookDelivery;
import com.secretvault.webhook.entity.WebhookEndpoint;
import com.secretvault.webhook.service.WebhookDeliveryService;
import com.secretvault.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "Webhooks", description = "Secure outbound webhook endpoints, signing, SSRF defense, and delivery tracking")
@SecurityRequirement(name = "BearerAuth")
public class WebhookController {

    private final WebhookService webhookService;
    private final WebhookDeliveryService deliveryService;

    public WebhookController(WebhookService webhookService, WebhookDeliveryService deliveryService) {
        this.webhookService = webhookService;
        this.deliveryService = deliveryService;
    }

    public record CreateWebhookRequest(
            @NotBlank String name,
            @NotBlank String destinationUrl,
            List<String> subscribedEvents,
            boolean enabled
    ) {}

    public record UpdateWebhookRequest(
            String name,
            String destinationUrl,
            List<String> subscribedEvents,
            Boolean enabled
    ) {}

    @PostMapping("/webhooks")
    @Operation(summary = "Create webhook endpoint")
    public ResponseEntity<ApiResponse<WebhookEndpoint>> createWebhook(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateWebhookRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        WebhookEndpoint endpoint = webhookService.createEndpoint(
                workspaceId,
                req.name(),
                req.destinationUrl(),
                req.subscribedEvents(),
                actorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(endpoint, "Webhook endpoint created successfully")
        );
    }

    @GetMapping("/webhooks")
    @Operation(summary = "List webhook endpoints")
    public ResponseEntity<ApiResponse<Page<WebhookEndpoint>>> listWebhooks(
            @PathVariable UUID workspaceId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<WebhookEndpoint> endpoints = webhookService.getEndpoints(workspaceId, actorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(endpoints));
    }

    @GetMapping("/webhooks/{webhookId}")
    @Operation(summary = "Get webhook endpoint details")
    public ResponseEntity<ApiResponse<WebhookEndpoint>> getWebhook(
            @PathVariable UUID workspaceId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        WebhookEndpoint endpoint = webhookService.getEndpoint(workspaceId, webhookId, actorId);
        return ResponseEntity.ok(ApiResponse.success(endpoint));
    }

    @PutMapping("/webhooks/{webhookId}")
    @Operation(summary = "Update webhook endpoint")
    public ResponseEntity<ApiResponse<WebhookEndpoint>> updateWebhook(
            @PathVariable UUID workspaceId,
            @PathVariable UUID webhookId,
            @RequestBody UpdateWebhookRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        WebhookEndpoint endpoint = webhookService.updateEndpoint(
                workspaceId,
                webhookId,
                req.name(),
                req.destinationUrl(),
                req.enabled(),
                req.subscribedEvents(),
                actorId
        );
        return ResponseEntity.ok(ApiResponse.success(endpoint, "Webhook endpoint updated successfully"));
    }

    @DeleteMapping("/webhooks/{webhookId}")
    @Operation(summary = "Delete webhook endpoint")
    public ResponseEntity<ApiResponse<Void>> deleteWebhook(
            @PathVariable UUID workspaceId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        webhookService.deleteEndpoint(workspaceId, webhookId, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook endpoint deleted successfully"));
    }

    @GetMapping("/webhooks/{webhookId}/signing-secret")
    @Operation(summary = "Reveal webhook signing secret")
    public ResponseEntity<ApiResponse<Map<String, String>>> revealSigningSecret(
            @PathVariable UUID workspaceId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        String secret = webhookService.revealSigningSecretForOperator(workspaceId, webhookId, actorId);
        WebhookEndpoint endpoint = webhookService.getEndpoint(workspaceId, webhookId, actorId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("signingSecret", secret, "secretPrefix", endpoint.getSecretPrefix()),
                "Signing secret revealed successfully"
        ));
    }

    @PostMapping("/webhooks/{webhookId}/rotate-secret")
    @Operation(summary = "Rotate webhook signing secret with transition grace period")
    public ResponseEntity<ApiResponse<Map<String, String>>> rotateSigningSecret(
            @PathVariable UUID workspaceId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        String newSecret = webhookService.rotateSigningSecret(workspaceId, webhookId, actorId);
        WebhookEndpoint endpoint = webhookService.getEndpoint(workspaceId, webhookId, actorId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("signingSecret", newSecret, "secretPrefix", endpoint.getSecretPrefix()),
                "Signing secret rotated successfully"
        ));
    }

    @PostMapping("/webhooks/{webhookId}/test")
    @Operation(summary = "Dispatch a test webhook ping to the endpoint")
    public ResponseEntity<ApiResponse<WebhookDelivery>> testWebhookEndpoint(
            @PathVariable UUID workspaceId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        WebhookDelivery delivery = deliveryService.testEndpoint(workspaceId, webhookId, actorId);
        return ResponseEntity.ok(ApiResponse.success(delivery, "Test webhook ping dispatched"));
    }

    @GetMapping({"/webhook-deliveries", "/webhooks/{webhookId}/deliveries"})
    @Operation(summary = "List webhook delivery attempts and logs")
    public ResponseEntity<ApiResponse<Page<WebhookDelivery>>> listDeliveries(
            @PathVariable UUID workspaceId,
            @PathVariable(required = false) UUID webhookId,
            @RequestParam(required = false) UUID queryWebhookId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID targetWebhookId = webhookId != null ? webhookId : queryWebhookId;
        UUID actorId = principal != null ? principal.getId() : null;
        Page<WebhookDelivery> deliveries = deliveryService.getDeliveries(workspaceId, targetWebhookId, actorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(deliveries));
    }

    @PostMapping({"/webhook-deliveries/{deliveryId}/replay", "/webhooks/deliveries/{deliveryId}/retry"})
    @Operation(summary = "Replay failed webhook delivery")
    public ResponseEntity<ApiResponse<WebhookDelivery>> replayDelivery(
            @PathVariable UUID workspaceId,
            @PathVariable UUID deliveryId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        WebhookDelivery delivery = deliveryService.replayDelivery(workspaceId, deliveryId, actorId);
        return ResponseEntity.ok(ApiResponse.success(delivery, "Webhook delivery replayed"));
    }
}
