package com.secretvault.auth.mfa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthMfaLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TotpService totpService;

    @Test
    @DisplayName("Complete MFA Login Flow: Register -> Single-Factor Login -> Enroll & Activate MFA -> Password Login returns Challenge -> TOTP Verification Issues Tokens")
    void testCompleteMfaTotpLoginFlow() throws Exception {
        String email = "mfa_login_test_" + UUID.randomUUID() + "@example.com";
        String password = "Password123!Secure";

        // 1. Register User
        RegisterRequest registerReq = new RegisterRequest(email, password, "MFA Test User", "MFA Org");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mfaRequired").value(false))
                .andExpect(jsonPath("$.data.accessToken").isString());

        // 2. Initial Login with MFA disabled -> returns tokens directly
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult loginResult1 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(false))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn();

        String sessionToken = objectMapper.readTree(loginResult1.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 3. Enroll MFA
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("Authorization", "Bearer " + sessionToken))
                .andExpect(status().isOk())
                .andReturn();

        String secret = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .path("data").path("secret").asText();

        // 4. Activate MFA using valid TOTP code
        String activationCode = totpService.generateCode(secret);
        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaActivateRequest(activationCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        // 5. Attempt login with password now that MFA is active -> MUST return MFA_REQUIRED with challengeId and NO tokens
        MvcResult mfaLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.mfaChallengeId").isString())
                .andExpect(jsonPath("$.data.mfaExpiresAt").isString())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String challengeId = objectMapper.readTree(mfaLoginResult.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();
        assertNotNull(challengeId);

        // 6. Complete TOTP Challenge Verification via public /api/v1/auth/mfa/verify-totp
        String loginTotpCode = totpService.generateCode(secret);
        MfaTotpVerifyRequest verifyReq = new MfaTotpVerifyRequest(challengeId, loginTotpCode);

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(false))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.activeWorkspace.name").value("Default Workspace"))
                .andReturn();

        String newAccessToken = objectMapper.readTree(verifyResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 7. Verify the new access token can access protected endpoints
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    @DisplayName("Complete MFA Recovery Code Login Flow: Login -> Challenge -> Recovery Code Verification -> Code Consumed & Decremented")
    void testCompleteMfaRecoveryCodeLoginFlow() throws Exception {
        String email = "mfa_recovery_login_" + UUID.randomUUID() + "@example.com";
        String password = "Password123!Secure";

        // 1. Register & Enroll & Activate
        RegisterRequest registerReq = new RegisterRequest(email, password, "Recovery User", "Recovery Org");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        MvcResult enrollRes = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String secret = objectMapper.readTree(enrollRes.getResponse().getContentAsString())
                .path("data").path("secret").asText();
        String code = totpService.generateCode(secret);

        MvcResult actRes = mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaActivateRequest(code))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode recoveryNodes = objectMapper.readTree(actRes.getResponse().getContentAsString())
                .path("data").path("recoveryCodes");
        List<String> recoveryCodes = new ArrayList<>();
        for (JsonNode n : recoveryNodes) {
            recoveryCodes.add(n.asText());
        }
        assertEquals(10, recoveryCodes.size());
        String codeToUse = recoveryCodes.get(0);

        // 2. Password Login -> Get MFA Challenge
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult mfaLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andReturn();

        String challengeId1 = objectMapper.readTree(mfaLoginRes.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        // 3. Verify using Recovery Code
        MfaRecoveryVerifyRequest recReq = new MfaRecoveryVerifyRequest(challengeId1, codeToUse);
        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String verifiedToken = objectMapper.readTree(verifyRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 4. Verify status now shows 9 remaining recovery codes
        mockMvc.perform(get("/api/v1/auth/mfa/status")
                        .header("Authorization", "Bearer " + verifiedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingRecoveryCodes").value(9));

        // 5. New Login Challenge -> Attempting to REPLAY the same recovery code MUST FAIL
        MvcResult mfaLoginRes2 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId2 = objectMapper.readTree(mfaLoginRes2.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        MfaRecoveryVerifyRequest replayReq = new MfaRecoveryVerifyRequest(challengeId2, codeToUse);
        mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replayReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("MFA Challenge Fail-Closed Security: 5 failed verification attempts locks and destroys the challenge")
    void testChallengeLockoutAfterFiveFailedAttempts() throws Exception {
        String email = "mfa_lockout_" + UUID.randomUUID() + "@example.com";
        String password = "Password123!Secure";

        // Register and activate MFA
        RegisterRequest registerReq = new RegisterRequest(email, password, "Lockout User", "Lockout Org");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        MvcResult enrollRes = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String secret = objectMapper.readTree(enrollRes.getResponse().getContentAsString())
                .path("data").path("secret").asText();
        String validCode = totpService.generateCode(secret);

        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaActivateRequest(validCode))))
                .andExpect(status().isOk());

        // Login to get challenge
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult mfaLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = objectMapper.readTree(mfaLoginRes.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        // Perform 5 failed attempts with invalid code
        MfaTotpVerifyRequest badReq = new MfaTotpVerifyRequest(challengeId, "000000");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt with VALID code MUST now FAIL because challenge has been locked and deleted
        String correctCode = totpService.generateCode(secret);
        MfaTotpVerifyRequest correctReq = new MfaTotpVerifyRequest(challengeId, correctCode);
        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctReq)))
                .andExpect(status().isUnauthorized());
    }
}
