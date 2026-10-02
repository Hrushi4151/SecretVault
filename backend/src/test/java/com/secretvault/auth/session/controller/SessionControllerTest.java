package com.secretvault.auth.session.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record AuthTokens(String accessToken, String refreshToken, String email) {}

    private AuthTokens registerUser(String prefix) throws Exception {
        String email = prefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Password123!Secure", "Session User", "Session Org");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(body).path("data").path("accessToken").asText();
        String refreshToken = objectMapper.readTree(body).path("data").path("refreshToken").asText();

        return new AuthTokens(accessToken, refreshToken, email);
    }

    @Test
    @DisplayName("GET /api/v1/auth/sessions requires authentication")
    void testListSessionsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/sessions returns current session with safe metadata and no-store header")
    void testListSessionsAuthenticated() throws Exception {
        AuthTokens user = registerUser("session_list");

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(jsonPath("$.data[0].revoked").value(false))
                .andExpect(jsonPath("$.data[0].authMethod").value("PASSWORD"))
                .andExpect(jsonPath("$.data[0].createdAt").isString())
                .andExpect(jsonPath("$.data[0].lastUsedAt").isString())
                // Ensure zero secret leakage
                .andExpect(jsonPath("$.data[0].refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data[0].tokenHash").doesNotExist())
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/sessions/{sessionId} revokes specific session and invalidates refresh")
    void testRevokeSpecificSession() throws Exception {
        AuthTokens user = registerUser("session_revoke");

        // 1. Get session ID
        MvcResult listResult = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        String sessionId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // 2. Revoke the session
        mockMvc.perform(delete("/api/v1/auth/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Attempting to use the refresh token from revoked session must fail
        com.secretvault.auth.dto.RefreshTokenRequest refreshReq = new com.secretvault.auth.dto.RefreshTokenRequest(user.refreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("IDOR: User A cannot revoke User B session")
    void testIdorRevokeRejection() throws Exception {
        AuthTokens userA = registerUser("user_a");
        AuthTokens userB = registerUser("user_b");

        // User A gets their session ID
        MvcResult listResultA = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + userA.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        String sessionAId = objectMapper.readTree(listResultA.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // User B tries to revoke User A's session -> Must fail with 404
        mockMvc.perform(delete("/api/v1/auth/sessions/" + sessionAId)
                        .header("Authorization", "Bearer " + userB.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User A's refresh token must remain usable
        com.secretvault.auth.dto.RefreshTokenRequest refreshReq = new com.secretvault.auth.dto.RefreshTokenRequest(userA.refreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/auth/sessions/revoke-others terminates secondary sessions while keeping current active")
    void testRevokeAllOtherSessions() throws Exception {
        AuthTokens user = registerUser("revoke_others");

        // 1. Simulate second login to establish second session
        com.secretvault.auth.dto.LoginRequest loginReq = new com.secretvault.auth.dto.LoginRequest(user.email(), "Password123!Secure");
        MvcResult secondLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String session2AccessToken = objectMapper.readTree(secondLoginResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        String session2RefreshToken = objectMapper.readTree(secondLoginResult.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // Verify 2 active sessions exist
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + session2AccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // 2. Caller on session 2 calls revoke-others
        mockMvc.perform(post("/api/v1/auth/sessions/revoke-others")
                        .header("Authorization", "Bearer " + session2AccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Session 1 refresh token must now FAIL
        com.secretvault.auth.dto.RefreshTokenRequest refreshReq1 = new com.secretvault.auth.dto.RefreshTokenRequest(user.refreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isUnauthorized());

        // 4. Session 2 refresh token must SUCCEED
        com.secretvault.auth.dto.RefreshTokenRequest refreshReq2 = new com.secretvault.auth.dto.RefreshTokenRequest(session2RefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString());
    }

    @Test
    @DisplayName("POST /api/v1/auth/sessions/revoke-all terminates all sessions globally")
    void testRevokeAllSessions() throws Exception {
        AuthTokens user = registerUser("revoke_all");

        mockMvc.perform(post("/api/v1/auth/sessions/revoke-all")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Refresh token must now FAIL
        com.secretvault.auth.dto.RefreshTokenRequest refreshReq = new com.secretvault.auth.dto.RefreshTokenRequest(user.refreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());
    }
}
