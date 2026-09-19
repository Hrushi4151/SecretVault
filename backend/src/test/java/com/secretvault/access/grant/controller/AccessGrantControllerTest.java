package com.secretvault.access.grant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.grant.dto.AccessGrantResponse;
import com.secretvault.access.grant.dto.CreateAccessGrantRequest;
import com.secretvault.access.grant.service.AccessGrantService;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.service.EffectiveAccessService;
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
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccessGrantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private SecretRepository secretRepository;

    @Autowired
    private AccessGrantService accessGrantService;

    private String ownerToken;
    private UUID ownerId;
    private UUID workspaceId;
    private String developerToken;
    private UUID developerId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;

    @BeforeEach
    void setUp() {
        // Register Owner
        String ownerEmail = "grant_owner_" + UUID.randomUUID() + "@example.com";
        AuthResponse ownerAuth = authService.register(new RegisterRequest(ownerEmail, "Password123!", "Grant Owner", "Acme Org"));
        ownerToken = ownerAuth.accessToken();
        ownerId = ownerAuth.user().id();
        workspaceId = ownerAuth.activeWorkspace().id();

        // Register Developer
        String devEmail = "grant_dev_" + UUID.randomUUID() + "@example.com";
        AuthResponse devAuth = authService.register(new RegisterRequest(devEmail, "Password123!", "Grant Dev", "Dev Org"));
        developerToken = devAuth.accessToken();
        developerId = devAuth.user().id();

        // Add developer to owner's workspace
        workspaceService.addMember(workspaceId, new com.secretvault.workspace.dto.AddMemberRequest(devEmail, WorkspaceRole.DEVELOPER), ownerId);

        // Seed Project, Environment, Secret
        Project project = new Project(workspaceId, "Core API", "core-api-" + UUID.randomUUID(), "Core Desc", ownerId);
        project = projectRepository.save(project);
        projectId = project.getId();

        Environment env = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "Prod Desc", true, ownerId);
        env = environmentRepository.save(env);
        environmentId = env.getId();

        Secret secret = new Secret(environmentId, "API_KEY", "Key Desc", ownerId);
        secret = secretRepository.save(secret);
        secretId = secret.getId();
    }

    @Test
    @DisplayName("POST /access/grants: Successfully creates secret-level grant")
    void testCreateGrantEndpointSuccess() throws Exception {
        CreateAccessGrantRequest req = new CreateAccessGrantRequest(
                developerId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL
        );

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access/grants", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scopeType").value("SECRET"))
                .andExpect(jsonPath("$.data.permissionCode").value("secret.reveal"))
                .andExpect(jsonPath("$.data.userId").value(developerId.toString()))
                .andExpect(jsonPath("$.data.secretKey").value("API_KEY"));
    }

    @Test
    @DisplayName("POST /access/grants: Unauthorized actor returns 403 Forbidden")
    void testCreateGrantUnauthorizedForbidden() throws Exception {
        CreateAccessGrantRequest req = new CreateAccessGrantRequest(
                developerId, AccessScope.SECRET, projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL
        );

        // Developer tries to grant themselves permissions
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access/grants", workspaceId)
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /access/grants: Successfully lists and filters grants with pagination")
    void testListGrantsEndpoint() throws Exception {
        // Create a grant
        CreateAccessGrantRequest req = new CreateAccessGrantRequest(
                developerId, AccessScope.PROJECT, projectId, null, null, AccessPermission.SECRET_READ
        );
        accessGrantService.createGrant(workspaceId, req, ownerId);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/access/grants", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("scopeType", "PROJECT")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].scopeType").value("PROJECT"))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET /access/grants/{grantId}: Retrieves grant detail")
    void testGetGrantDetailEndpoint() throws Exception {
        CreateAccessGrantRequest req = new CreateAccessGrantRequest(
                developerId, AccessScope.PROJECT, projectId, null, null, AccessPermission.SECRET_READ
        );
        AccessGrantResponse created = accessGrantService.createGrant(workspaceId, req, ownerId);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/access/grants/{grantId}", workspaceId, created.id())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(created.id().toString()))
                .andExpect(jsonPath("$.data.userEmail").isNotEmpty());
    }

    @Test
    @DisplayName("DELETE /access/grants/{grantId}: Successfully revokes grant")
    void testRevokeGrantEndpoint() throws Exception {
        CreateAccessGrantRequest req = new CreateAccessGrantRequest(
                developerId, AccessScope.PROJECT, projectId, null, null, AccessPermission.SECRET_READ
        );
        AccessGrantResponse created = accessGrantService.createGrant(workspaceId, req, ownerId);

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/access/grants/{grantId}", workspaceId, created.id())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Subsequent lookup returns 404
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/access/grants/{grantId}", workspaceId, created.id())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("IDOR Defense: Cannot query grant from a foreign workspace")
    void testIdorForeignWorkspaceGrantBlocked() throws Exception {
        // Register Foreign Workspace
        String foreignEmail = "foreign_owner_" + UUID.randomUUID() + "@example.com";
        AuthResponse foreignAuth = authService.register(new RegisterRequest(foreignEmail, "Password123!", "Foreign Owner", "Foreign Org"));
        String foreignToken = foreignAuth.accessToken();
        UUID foreignWsId = foreignAuth.activeWorkspace().id();

        CreateAccessGrantRequest req = new CreateAccessGrantRequest(
                developerId, AccessScope.WORKSPACE, null, null, null, AccessPermission.SECRET_READ
        );
        AccessGrantResponse created = accessGrantService.createGrant(workspaceId, req, ownerId);

        // Foreign user tries to query workspaceId's grant
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/access/grants/{grantId}", foreignWsId, created.id())
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /access/effective: Explains effective permissions for caller")
    void testEffectiveAccessExplanationEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/access/effective", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("projectId", projectId.toString())
                        .param("environmentId", environmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(13)))
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'secret.reveal')].granted").value(hasItem(true)));
    }
}
