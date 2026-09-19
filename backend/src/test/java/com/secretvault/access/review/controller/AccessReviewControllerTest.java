package com.secretvault.access.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.grant.dto.CreateAccessGrantRequest;
import com.secretvault.access.grant.service.AccessGrantService;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.review.dto.AccessReviewCampaignResponse;
import com.secretvault.access.review.dto.CreateCampaignRequest;
import com.secretvault.access.review.dto.DecideReviewItemRequest;
import com.secretvault.access.review.entity.AccessReviewItem;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.entity.ReviewDecision;
import com.secretvault.access.review.repository.AccessReviewItemRepository;
import com.secretvault.access.review.service.AccessReviewService;
import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.service.AuthService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccessReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private SecretRepository secretRepository;

    @Autowired
    private AccessGrantService accessGrantService;

    @Autowired
    private AccessReviewService reviewService;

    @Autowired
    private AccessReviewItemRepository itemRepository;

    private String ownerToken;
    private UUID ownerId;
    private UUID workspaceId;
    private String developerToken;
    private UUID developerId;
    private String devEmail;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;

    @BeforeEach
    void setUp() {
        // Register Owner
        String ownerEmail = "review_owner_" + UUID.randomUUID() + "@example.com";
        AuthResponse ownerAuth = authService.register(new RegisterRequest(ownerEmail, "Password123!", "Review Owner", "Audit Org"));
        ownerToken = ownerAuth.accessToken();
        ownerId = ownerAuth.user().id();
        workspaceId = ownerAuth.activeWorkspace().id();

        // Register Developer
        devEmail = "review_dev_" + UUID.randomUUID() + "@example.com";
        AuthResponse devAuth = authService.register(new RegisterRequest(devEmail, "Password123!", "Review Dev", "Dev Org"));
        developerToken = devAuth.accessToken();
        developerId = devAuth.user().id();

        // Add developer to owner's workspace
        workspaceService.addMember(workspaceId, new AddMemberRequest(devEmail, WorkspaceRole.DEVELOPER), ownerId);

        // Seed Project, Environment, Secret
        Project project = new Project(workspaceId, "Compliance API", "compliance-api-" + UUID.randomUUID(), "Core Compliance", ownerId);
        project = projectRepository.save(project);
        projectId = project.getId();

        Environment env = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "Prod Enclave", true, ownerId);
        env = environmentRepository.save(env);
        environmentId = env.getId();

        Secret secret = new Secret(environmentId, "PCI_KEY", "PCI Secret", ownerId);
        secret = secretRepository.save(secret);
        secretId = secret.getId();

        // Create Granular Grant for developer
        accessGrantService.createGrant(
                workspaceId,
                new CreateAccessGrantRequest(developerId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL),
                ownerId
        );
    }

    @Test
    @DisplayName("POST /access-reviews: Successfully creates review campaign with snapshot")
    void testCreateCampaignEndpointSuccess() throws Exception {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "SOC 2 Q4 Certification",
                "Quarterly access review for SOC 2 Type II audit",
                AccessScope.WORKSPACE,
                null,
                null,
                Instant.now().plus(30, ChronoUnit.DAYS)
        );

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access-reviews", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("SOC 2 Q4 Certification"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.totalItemsCount").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("POST /access-reviews/{id}/items/{itemId}/decide: Certifies item and updates campaign progress")
    void testDecideReviewItemEndpointSuccess() throws Exception {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Q4 Audit", "Review", AccessScope.WORKSPACE, null, null, Instant.now().plus(30, ChronoUnit.DAYS)
        );
        AccessReviewCampaignResponse campaign = reviewService.createCampaign(workspaceId, req, ownerId);

        List<AccessReviewItem> items = itemRepository.findByCampaignId(campaign.id());
        assertFalse(items.isEmpty());
        AccessReviewItem targetItem = items.stream()
                .filter(i -> i.getUserId().equals(developerId))
                .findFirst()
                .orElse(items.get(0));

        DecideReviewItemRequest decisionReq = new DecideReviewItemRequest(ReviewDecision.KEEP, "Certified legitimate for role");

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access-reviews/{campaignId}/items/{itemId}/decide",
                        workspaceId, campaign.id(), targetItem.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("KEEP"))
                .andExpect(jsonPath("$.data.decisionReason").value("Certified legitimate for role"));
    }

    @Test
    @DisplayName("POST /access-reviews/{id}/items/{itemId}/revoke: Targeted revocation removes grant")
    void testRevokeReviewItemEndpointSuccess() throws Exception {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Grant Audit", "Review", AccessScope.WORKSPACE, null, null, Instant.now().plus(30, ChronoUnit.DAYS)
        );
        AccessReviewCampaignResponse campaign = reviewService.createCampaign(workspaceId, req, ownerId);

        List<AccessReviewItem> items = itemRepository.findByCampaignId(campaign.id());
        AccessReviewItem grantItem = items.stream()
                .filter(i -> i.getPermissionSummary().contains("secret.reveal"))
                .findFirst()
                .orElseThrow();

        DecideReviewItemRequest decisionReq = new DecideReviewItemRequest(ReviewDecision.REVOKE, "Revoking excessive permission");

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access-reviews/{campaignId}/items/{itemId}/revoke",
                        workspaceId, campaign.id(), grantItem.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("REVOKE"));
    }

    @Test
    @DisplayName("POST /access-reviews/{id}/complete: Finalizes campaign and emits attestation report")
    void testCompleteCampaignEndpointSuccess() throws Exception {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Final Audit", "Review", AccessScope.WORKSPACE, null, null, Instant.now().plus(30, ChronoUnit.DAYS)
        );
        AccessReviewCampaignResponse campaign = reviewService.createCampaign(workspaceId, req, ownerId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access-reviews/{campaignId}/complete",
                        workspaceId, campaign.id())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.campaignName").value("Final Audit"))
                .andExpect(jsonPath("$.data.certifiedByEmail").isNotEmpty());
    }

    @Test
    @DisplayName("GET /access-reviews/{id}/attestation: Retrieves attestation report")
    void testGetAttestationReportEndpointSuccess() throws Exception {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Ledger Report", "Review", AccessScope.WORKSPACE, null, null, Instant.now().plus(30, ChronoUnit.DAYS)
        );
        AccessReviewCampaignResponse campaign = reviewService.createCampaign(workspaceId, req, ownerId);
        reviewService.completeCampaign(workspaceId, campaign.id(), ownerId);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/access-reviews/{campaignId}/attestation",
                        workspaceId, campaign.id())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItemsReviewed").value(greaterThanOrEqualTo(1)));
    }
}
