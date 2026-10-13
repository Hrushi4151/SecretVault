package com.secretvault.machine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.machine.dto.MachineDtos;
import com.secretvault.machine.model.MachineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MachineIdentityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Create machine identity, issue direct token, and verify response")
    void testCreateMachineAndGenerateToken() throws Exception {
        // 1. Register User & get token
        String email = "machine_admin_" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Password123!Secure", "Machine Admin", "Enterprise Vault Corp");

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. Fetch default workspace
        MvcResult wsResult = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String workspaceId = objectMapper.readTree(wsResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // 3. Create machine identity
        String machineName = "k8s-pod-" + UUID.randomUUID().toString().substring(0, 8);
        MachineDtos.CreateMachineIdentityRequest createReq = new MachineDtos.CreateMachineIdentityRequest(
                machineName,
                "Automated k8s pod worker",
                MachineType.WORKLOAD,
                null,
                null
        );

        MvcResult machineResult = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/machine-identities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(machineName))
                .andReturn();

        String machineId = objectMapper.readTree(machineResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 4. Generate token with request body
        MachineDtos.GenerateMachineTokenRequest genReq = new MachineDtos.GenerateMachineTokenRequest(
                86400L,
                "Batch Worker Token"
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/machine-identities/" + machineId + "/tokens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(genReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").value(startsWith("sv_machine_")))
                .andExpect(jsonPath("$.data.tokenPrefix").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(86400));

        // 5. Generate token without request body (defaults)
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/machine-identities/" + machineId + "/tokens")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").value(startsWith("sv_machine_")));
    }
}
