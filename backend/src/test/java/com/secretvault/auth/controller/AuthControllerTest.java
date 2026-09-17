package com.secretvault.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/auth/register should successfully register user and return tokens")
    void testRegisterEndpointSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "controller_test_user@example.com",
                "SecurePassword123!",
                "Test Engineer",
                "Test Engineering Org"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.email").value("controller_test_user@example.com"))
                .andExpect(jsonPath("$.data.activeWorkspace.name").value("Default Workspace"))
                .andExpect(jsonPath("$.data.activeWorkspace.role").value("OWNER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with invalid email should return 400 Validation Error")
    void testRegisterValidationFailure() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "invalid-email",
                "123", // too short
                "",    // blank
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login and GET /api/v1/auth/me workflow")
    void testLoginAndMeWorkflow() throws Exception {
        // 1. Register user
        String email = "workflow_user@example.com";
        String password = "Password123!Secure";
        RegisterRequest registerReq = new RegisterRequest(email, password, "Workflow User", "Workflow Org");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // 2. Login
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseBody).path("data").path("accessToken").asText();

        // 3. Authenticate with Bearer token on /api/v1/auth/me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.fullName").value("Workflow User"));

        // 4. Request /api/v1/auth/me without token should return 401 Unauthorized / Forbidden
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh Token Rotation: Old refresh token is revoked upon use; replay is rejected")
    void testRefreshTokenRotationAndReplayRejection() throws Exception {
        String email = "refresh_user_" + java.util.UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Password123!Secure", "Refresh User", "Org Refresh");

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String rawRefreshToken1 = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // 1. First refresh exchange with rawRefreshToken1 -> Should SUCCEED
        com.secretvault.auth.dto.RefreshTokenRequest refreshReq1 = new com.secretvault.auth.dto.RefreshTokenRequest(rawRefreshToken1);
        MvcResult refreshResult1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn();

        String rawRefreshToken2 = objectMapper.readTree(refreshResult1.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // 2. Replaying rawRefreshToken1 -> MUST FAIL with 401 Unauthorized
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 3. Using rawRefreshToken2 -> Should SUCCEED
        com.secretvault.auth.dto.RefreshTokenRequest refreshReq2 = new com.secretvault.auth.dto.RefreshTokenRequest(rawRefreshToken2);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout should revoke all active refresh tokens for the user")
    void testLogoutInvalidation() throws Exception {
        String email = "logout_user_" + java.util.UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Password123!Secure", "Logout User", "Org Logout");

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        String refreshToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Attempt to use refresh token after logout -> MUST FAIL with 401 Unauthorized
        com.secretvault.auth.dto.RefreshTokenRequest refreshReq = new com.secretvault.auth.dto.RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
