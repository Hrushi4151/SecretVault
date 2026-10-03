package com.secretvault.auth.stepup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.stepup.dto.PasswordStepUpRequest;
import com.secretvault.auth.stepup.dto.StepUpChallengeRequest;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StepUpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record AuthTokens(String accessToken, String refreshToken, String email) {}

    private AuthTokens registerUser(String prefix) throws Exception {
        String email = prefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Password123!Secure", "StepUp User", "StepUp Org");

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
    @DisplayName("POST /api/v1/auth/step-up/challenges requires authentication")
    void testCreateChallenge_unauthenticated() throws Exception {
        StepUpChallengeRequest request = new StepUpChallengeRequest(StepUpAction.SESSION_REVOKE_ALL, StepUpContext.empty());
        mockMvc.perform(post("/api/v1/auth/step-up/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/step-up/challenges creates challenge for authenticated user with no-store header")
    void testCreateChallenge_success() throws Exception {
        AuthTokens tokens = registerUser("stepup_create");

        StepUpChallengeRequest request = new StepUpChallengeRequest(StepUpAction.SESSION_REVOKE_ALL, StepUpContext.empty());
        mockMvc.perform(post("/api/v1/auth/step-up/challenges")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.data.challengeId", notNullValue()))
                .andExpect(jsonPath("$.data.action", is("SESSION_REVOKE_ALL")))
                .andExpect(jsonPath("$.data.supportedFactors", hasItem("PASSWORD")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/step-up/challenges/{challengeId}/verify-password validates password and returns proof")
    void testVerifyPassword_success() throws Exception {
        AuthTokens tokens = registerUser("stepup_pwd");

        StepUpChallengeRequest challengeReq = new StepUpChallengeRequest(StepUpAction.SESSION_REVOKE_ALL, StepUpContext.empty());
        MvcResult challengeResult = mockMvc.perform(post("/api/v1/auth/step-up/challenges")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(challengeReq)))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = objectMapper.readTree(challengeResult.getResponse().getContentAsString())
                .path("data").path("challengeId").asText();

        PasswordStepUpRequest pwdReq = new PasswordStepUpRequest("Password123!Secure");
        mockMvc.perform(post("/api/v1/auth/step-up/challenges/" + challengeId + "/verify-password")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pwdReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.data.proofToken", startsWith("stup_")))
                .andExpect(jsonPath("$.data.action", is("SESSION_REVOKE_ALL")))
                .andExpect(jsonPath("$.data.factorUsed", is("PASSWORD")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/step-up/challenges/{challengeId}/verify-password with wrong password returns 401")
    void testVerifyPassword_wrongPassword() throws Exception {
        AuthTokens tokens = registerUser("stepup_wrong_pwd");

        StepUpChallengeRequest challengeReq = new StepUpChallengeRequest(StepUpAction.SESSION_REVOKE_ALL, StepUpContext.empty());
        MvcResult challengeResult = mockMvc.perform(post("/api/v1/auth/step-up/challenges")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(challengeReq)))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = objectMapper.readTree(challengeResult.getResponse().getContentAsString())
                .path("data").path("challengeId").asText();

        PasswordStepUpRequest pwdReq = new PasswordStepUpRequest("WrongPassword123!");
        mockMvc.perform(post("/api/v1/auth/step-up/challenges/" + challengeId + "/verify-password")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pwdReq)))
                .andExpect(status().isUnauthorized());
    }
}
