package com.secretvault.secret.reveal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.secret.reveal.dto.CreateRevealIntentRequest;
import com.secretvault.secret.reveal.dto.ExecuteRevealRequest;
import com.secretvault.secret.reveal.dto.UpdateSecretRevealPolicyRequest;
import com.secretvault.secret.reveal.model.RevealPolicyLevel;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecretRevealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String workspaceId;
    private String projectId;
    private String environmentId;
    private String secretId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "reveal_test_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regReq = new RegisterRequest(email, "Password123!Secure", "Reveal Tester", "SecCorp");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated()).andReturn();
        token = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + token)).andReturn();
        workspaceId = objectMapper.readTree(wsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        CreateProjectRequest projReq = new CreateProjectRequest("Auth Service", "auth-service-" + UUID.randomUUID(), "Security Service");
        MvcResult pRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated()).andReturn();
        projectId = objectMapper.readTree(pRes.getResponse().getContentAsString()).path("data").path("id").asText();

        MvcResult envsRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        environmentId = objectMapper.readTree(envsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        CreateSecretRequest secReq = new CreateSecretRequest("PAYMENT_GATEWAY_KEY", "sk_live_verysecret123456789", "Gateway Secret Key");
        MvcResult secRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secReq)))
                .andExpect(status().isCreated()).andReturn();
        secretId = objectMapper.readTree(secRes.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    @Test
    @DisplayName("GET /reveal-policy returns effective reveal policy metadata")
    void testGetRevealPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/reveal-policy")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyLevel").exists())
                .andExpect(jsonPath("$.data.maxDisplayDurationSeconds").isNumber())
                .andExpect(jsonPath("$.data.copyAllowed").isBoolean())
                .andExpect(jsonPath("$.data.clipboardTimeoutSeconds").isNumber());
    }

    @Test
    @DisplayName("POST /reveal-intent and POST /reveal two-phase flow decrypts secret with secure headers")
    void testTwoPhaseRevealFlow() throws Exception {
        CreateRevealIntentRequest intentReq = new CreateRevealIntentRequest(1, null, null);
        MvcResult intentRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/reveal-intent")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intentToken").isString())
                .andExpect(jsonPath("$.data.intentToken").isNotEmpty())
                .andReturn();

        String intentToken = objectMapper.readTree(intentRes.getResponse().getContentAsString()).path("data").path("intentToken").asText();

        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentToken, 1, null, null);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/reveal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(execReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.value").value("sk_live_verysecret123456789"))
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.maxDisplayDurationSeconds").isNumber());
    }

    @Test
    @DisplayName("POST /versions/{versionNumber}/reveal-intent and reveal historical version")
    void testVersionRevealFlow() throws Exception {
        CreateRevealIntentRequest intentReq = new CreateRevealIntentRequest(1, null, null);
        MvcResult intentRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/versions/1/reveal-intent")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intentToken").isString())
                .andReturn();

        String intentToken = objectMapper.readTree(intentRes.getResponse().getContentAsString()).path("data").path("intentToken").asText();

        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentToken, 1, null, null);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/versions/1/reveal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(execReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.value").value("sk_live_verysecret123456789"));
    }

    @Test
    @DisplayName("Reveal policy management CRUD and audit trail")
    void testRevealPolicyCrudAndAudit() throws Exception {
        UpdateSecretRevealPolicyRequest updateReq = new UpdateSecretRevealPolicyRequest(
                com.secretvault.access.privileged.model.PrivilegedPolicyScope.WORKSPACE,
                null,
                null,
                null,
                RevealPolicyLevel.PRODUCTION_CRITICAL,
                true,
                "WEBAUTHN",
                false,
                true,
                10,
                500,
                false,
                15,
                false,
                10,
                false,
                10,
                30,
                true
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/secret-reveal-policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyLevel").value("PRODUCTION_CRITICAL"))
                .andExpect(jsonPath("$.data.copyAllowed").value(false));

        // Get policies list
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/secret-reveal-policies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        // Audit records check
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/secret-reveal-audit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
