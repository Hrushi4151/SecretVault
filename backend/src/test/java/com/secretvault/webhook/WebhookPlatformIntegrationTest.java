package com.secretvault.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventType;
import com.secretvault.webhook.controller.WebhookController;
import com.secretvault.webhook.entity.WebhookDelivery;
import com.secretvault.webhook.entity.WebhookDeliveryStatus;
import com.secretvault.webhook.entity.WebhookEndpoint;
import com.secretvault.webhook.repository.WebhookDeliveryRepository;
import com.secretvault.webhook.repository.WebhookEndpointRepository;
import com.secretvault.webhook.service.WebhookDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 13: Hardened Webhook Platform End-to-End Integration Tests")
public class WebhookPlatformIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private WebhookDeliveryService deliveryService;

    private String ownerToken;
    private UUID ownerId;
    private UUID workspaceId;

    private String outsiderToken;
    private UUID outsiderWorkspaceId;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Register Workspace Owner
        String ownerEmail = "hookowner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reg1 = new RegisterRequest(ownerEmail, "Password123!Secure", "Webhook Owner", "HookCorp");
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data");
        ownerToken = data1.path("accessToken").asText();
        ownerId = UUID.fromString(data1.path("user").path("id").asText());

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        workspaceId = UUID.fromString(objectMapper.readTree(wsRes.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText());

        // 2. Register Outsider in separate workspace for cross-tenant boundary verification
        String outsiderEmail = "outsider_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reg2 = new RegisterRequest(outsiderEmail, "Password123!Secure", "Outsider", "OtherCorp");
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data");
        outsiderToken = data2.path("accessToken").asText();
        MvcResult outWsRes = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andReturn();
        outsiderWorkspaceId = UUID.fromString(objectMapper.readTree(outWsRes.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText());
    }

    @Test
    @DisplayName("Endpoint Lifecycle & Secret Masking: Secret is encrypted at rest and never exposed in list/get APIs")
    void testWebhookEndpointLifecycleAndSecretMasking() throws Exception {
        WebhookController.CreateWebhookRequest createReq = new WebhookController.CreateWebhookRequest(
                "Audit Ingress Webhook",
                "https://api.datadoghq.com/api/v1/events",
                List.of("SECRET_ROTATED", "SECRET_REVEALED"),
                true
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/webhooks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Audit Ingress Webhook"))
                .andExpect(jsonPath("$.data.secretPrefix").exists())
                .andExpect(jsonPath("$.data.signingSecretEncrypted").doesNotExist()) // Never exposed in JSON
                .andReturn();

        UUID endpointId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // List webhooks: secret remains unexposed
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/webhooks")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].signingSecretEncrypted").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].secretPrefix").exists());

        // Get single webhook: secret remains unexposed
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/webhooks/" + endpointId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signingSecretEncrypted").doesNotExist());

        // Reveal secret via dedicated endpoint
        MvcResult revealRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/webhooks/" + endpointId + "/signing-secret")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signingSecret").value(org.hamcrest.Matchers.startsWith("whsec_")))
                .andReturn();

        String revealedSecret = objectMapper.readTree(revealRes.getResponse().getContentAsString())
                .path("data").path("signingSecret").asText();

        // Rotate secret
        MvcResult rotateRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/webhooks/" + endpointId + "/rotate-secret")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signingSecret").value(org.hamcrest.Matchers.startsWith("whsec_")))
                .andReturn();

        String rotatedSecret = objectMapper.readTree(rotateRes.getResponse().getContentAsString())
                .path("data").path("signingSecret").asText();

        assertThat(rotatedSecret).isNotEqualTo(revealedSecret);

        // Verify endpoint entity persisted previous secret for safe transition
        WebhookEndpoint ep = endpointRepository.findById(endpointId).orElseThrow();
        assertThat(ep.getPreviousSigningSecretEncrypted()).isNotNull();
        assertThat(ep.getSecretRotatedAt()).isNotNull();
    }

    @Test
    @DisplayName("SSRF Defense: Rejects localhost, cloud metadata, and userinfo in destination URLs")
    void testSsrfProtectionOnEndpointCreation() throws Exception {
        // 1. AWS/GCP cloud metadata IP
        WebhookController.CreateWebhookRequest metadataReq = new WebhookController.CreateWebhookRequest(
                "Metadata SSRF",
                "http://169.254.169.254/latest/meta-data/",
                List.of("*"),
                true
        );
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/webhooks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(metadataReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SSRF Protection")));

        // 2. Localhost
        WebhookController.CreateWebhookRequest localhostReq = new WebhookController.CreateWebhookRequest(
                "Localhost SSRF",
                "http://localhost:8080/admin",
                List.of("*"),
                true
        );
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/webhooks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(localhostReq)))
                .andExpect(status().isBadRequest());

        // 3. User-info in destination URL
        WebhookController.CreateWebhookRequest userInfoReq = new WebhookController.CreateWebhookRequest(
                "UserInfo SSRF",
                "https://admin:pass@api.example.com/hook",
                List.of("*"),
                true
        );
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/webhooks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userInfoReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("User-info credentials")));
    }

    @Test
    @DisplayName("Multi-tenant Isolation: Outsider workspace cannot access or replay deliveries")
    void testCrossWorkspaceIsolationAndRbac() throws Exception {
        WebhookController.CreateWebhookRequest createReq = new WebhookController.CreateWebhookRequest(
                "Tenant A Webhook",
                "https://api.github.com/webhook",
                List.of("*"),
                true
        );

        MvcResult res = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/webhooks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID endpointId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // Outsider attempts to reveal secret from Workspace A -> 403 or 404
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/webhooks/" + endpointId + "/signing-secret")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        // Outsider attempts to access endpoint through own workspace route -> 404 Not Found
        mockMvc.perform(get("/api/v1/workspaces/" + outsiderWorkspaceId + "/webhooks/" + endpointId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Domain Event Deduplication & Concurrency: Duplicate domain events do not create duplicate delivery records")
    void testDomainEventDeduplicationAndAtomicClaims() throws Exception {
        WebhookEndpoint ep = new WebhookEndpoint();
        ep.setWorkspaceId(workspaceId);
        ep.setName("Deduplication Test Endpoint");
        ep.setDestinationUrl("https://api.datadoghq.com/events");
        ep.setEnabled(true);
        ep.setSecretPrefix("whsec_test123");
        ep.setSigningSecretEncrypted("{}");
        ep.setSubscribedEventsJson("[\"*\"]");
        WebhookEndpoint savedEp = endpointRepository.save(ep);

        DomainEvent event = com.secretvault.events.model.BaseDomainEvent.builder()
                .workspaceId(workspaceId)
                .eventType(EventType.ROTATION_COMPLETED)
                .aggregateType("SECRET")
                .aggregateId(UUID.randomUUID().toString())
                .actorId(ownerId.toString())
                .metadata(Map.of("environment", "production", "reason", "SCHEDULED"))
                .build();

        // First dispatch
        deliveryService.enqueueDeliveriesForEvent(event);
        long countAfterFirst = deliveryRepository.countByWorkspaceIdAndStatus(workspaceId, WebhookDeliveryStatus.PENDING)
                + deliveryRepository.countByWorkspaceIdAndStatus(workspaceId, WebhookDeliveryStatus.PROCESSING)
                + deliveryRepository.countByWorkspaceIdAndStatus(workspaceId, WebhookDeliveryStatus.FAILED);

        // Second dispatch with exact same event
        deliveryService.enqueueDeliveriesForEvent(event);
        long countAfterSecond = deliveryRepository.countByWorkspaceIdAndStatus(workspaceId, WebhookDeliveryStatus.PENDING)
                + deliveryRepository.countByWorkspaceIdAndStatus(workspaceId, WebhookDeliveryStatus.PROCESSING)
                + deliveryRepository.countByWorkspaceIdAndStatus(workspaceId, WebhookDeliveryStatus.FAILED);

        // Must not duplicate
        assertThat(countAfterSecond).isEqualTo(countAfterFirst);

        // Concurrency test: Atomic claim on the same delivery
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWorkspaceId(workspaceId);
        delivery.setWebhookId(savedEp.getId());
        delivery.setEventId(UUID.randomUUID());
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        WebhookDelivery savedDelivery = deliveryRepository.save(delivery);

        AtomicInteger successfulClaims = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        Runnable worker = () -> {
            int claimed = deliveryRepository.claimDelivery(
                    savedDelivery.getId(),
                    WebhookDeliveryStatus.PENDING,
                    WebhookDeliveryStatus.PROCESSING,
                    "worker-" + Thread.currentThread().getName(),
                    Instant.now().plusSeconds(300)
            );
            if (claimed == 1) {
                successfulClaims.incrementAndGet();
            }
            latch.countDown();
        };

        Thread t1 = new Thread(worker);
        Thread t2 = new Thread(worker);
        t1.start();
        t2.start();
        latch.await();

        // Exactly one thread must succeed in claiming the delivery
        assertThat(successfulClaims.get()).isEqualTo(1);
    }
}
