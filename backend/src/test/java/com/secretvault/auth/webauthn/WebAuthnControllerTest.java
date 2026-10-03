package com.secretvault.auth.webauthn;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebAuthnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record AuthTokens(String accessToken, String refreshToken, String email) {}

    private AuthTokens registerUser(String prefix) throws Exception {
        String email = prefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Password123!Secure", "WebAuthn User", "WebAuthn Org");

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
    @DisplayName("POST /api/v1/auth/webauthn/registration/options requires authentication")
    void testStartRegistration_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/webauthn/registration/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/webauthn/registration/options returns creation options for authenticated user")
    void testStartRegistration_authenticated() throws Exception {
        AuthTokens tokens = registerUser("webauthn_reg");

        mockMvc.perform(post("/api/v1/auth/webauthn/registration/options")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .param("friendlyName", "Test Passkey"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.challengeId", notNullValue()))
                .andExpect(jsonPath("$.optionsJson", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/webauthn/authentication/options is public and returns assertion options")
    void testStartAuthentication_public() throws Exception {
        mockMvc.perform(post("/api/v1/auth/webauthn/authentication/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.challengeId", notNullValue()))
                .andExpect(jsonPath("$.optionsJson", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/auth/webauthn/credentials requires authentication and returns empty list initially")
    void testListCredentials_empty() throws Exception {
        AuthTokens tokens = registerUser("webauthn_list");

        mockMvc.perform(get("/api/v1/auth/webauthn/credentials")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
