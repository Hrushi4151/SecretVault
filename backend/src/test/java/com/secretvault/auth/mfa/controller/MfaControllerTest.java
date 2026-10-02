package com.secretvault.auth.mfa.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.mfa.totp.TotpService;
import com.secretvault.auth.mfa.dto.MfaActivateRequest;
import com.secretvault.auth.mfa.dto.MfaRecoveryVerifyRequest;
import com.secretvault.auth.mfa.dto.MfaTotpVerifyRequest;
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
class MfaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TotpService totpService;

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest registerReq = new RegisterRequest(email, "Password123!Secure", "Test User", "Test Org");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("GET /api/v1/auth/mfa/status should return DISABLED for newly registered user")
    void testGetMfaStatusInitiallyDisabled() throws Exception {
        String email = "mfa_status_" + UUID.randomUUID() + "@example.com";
        String token = registerAndGetToken(email);

        mockMvc.perform(get("/api/v1/auth/mfa/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.remainingRecoveryCodes").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/auth/mfa/enroll and /activate workflow")
    void testEnrollAndActivateWorkflow() throws Exception {
        String email = "mfa_enroll_" + UUID.randomUUID() + "@example.com";
        String token = registerAndGetToken(email);

        // 1. Enroll
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").isString())
                .andExpect(jsonPath("$.data.provisioningUri").isString())
                .andReturn();

        String secret = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .path("data").path("secret").asText();

        // 2. Generate valid TOTP code from secret
        String validCode = totpService.generateCode(secret);

        // 3. Activate
        MfaActivateRequest activateReq = new MfaActivateRequest(validCode);
        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.recoveryCodes", hasSize(10)));

        // 4. Status should now be ENABLED with 10 recovery codes
        mockMvc.perform(get("/api/v1/auth/mfa/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.remainingRecoveryCodes").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/auth/mfa/activate with invalid code should fail")
    void testActivateWithInvalidCodeFails() throws Exception {
        String email = "mfa_invalid_act_" + UUID.randomUUID() + "@example.com";
        String token = registerAndGetToken(email);

        mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MfaActivateRequest activateReq = new MfaActivateRequest("000000");
        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activateReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/mfa/disable with valid password and TOTP should disable MFA")
    void testDisableMfaWorkflow() throws Exception {
        String email = "mfa_disable_" + UUID.randomUUID() + "@example.com";
        String password = "Password123!Secure";
        String token = registerAndGetToken(email);

        // Enroll and activate
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String secret = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .path("data").path("secret").asText();
        String validCode = totpService.generateCode(secret);

        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaActivateRequest(validCode))))
                .andExpect(status().isOk());

        // 1. Attempt disable with wrong password -> MUST FAIL with 401
        com.secretvault.auth.mfa.dto.MfaDisableRequest badPassReq = new com.secretvault.auth.mfa.dto.MfaDisableRequest("WrongPassword!", validCode, null);
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPassReq)))
                .andExpect(status().isUnauthorized());

        // 2. Attempt disable with wrong TOTP -> MUST FAIL with 401
        com.secretvault.auth.mfa.dto.MfaDisableRequest badCodeReq = new com.secretvault.auth.mfa.dto.MfaDisableRequest(password, "000000", null);
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCodeReq)))
                .andExpect(status().isUnauthorized());

        // 3. Disable with valid password and fresh TOTP code
        String freshCode = totpService.generateCode(secret);
        com.secretvault.auth.mfa.dto.MfaDisableRequest goodReq = new com.secretvault.auth.mfa.dto.MfaDisableRequest(password, freshCode, null);
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodReq)))
                .andExpect(status().isOk());

        // Verify status is DISABLED
        mockMvc.perform(get("/api/v1/auth/mfa/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    @DisplayName("Protected MFA endpoints require Bearer authentication")
    void testUnauthenticatedAccessRejection() throws Exception {
        mockMvc.perform(get("/api/v1/auth/mfa/status"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/mfa/enroll"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }
}
