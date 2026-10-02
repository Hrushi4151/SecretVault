package com.secretvault.auth.mfa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.dto.MfaActivateRequest;
import com.secretvault.auth.mfa.dto.MfaDisableRequest;
import com.secretvault.auth.mfa.dto.MfaRecoveryVerifyRequest;
import com.secretvault.auth.mfa.dto.MfaTotpVerifyRequest;
import com.secretvault.auth.mfa.totp.TotpService;
import com.secretvault.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MfaSecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TotpService totpService;

    @Autowired
    private UserRepository userRepository;

    private static final AtomicInteger IP_COUNTER = new AtomicInteger(100);

    private String nextClientIp() {
        int val = IP_COUNTER.incrementAndGet();
        return "10." + ((val / 65536) % 250 + 1) + "." + ((val / 256) % 250 + 1) + "." + (val % 250 + 1);
    }

    private static class UserContext {
        final String email;
        final String password;
        final String token;
        final String secret;
        final List<String> recoveryCodes;

        UserContext(String email, String password, String token, String secret, List<String> recoveryCodes) {
            this.email = email;
            this.password = password;
            this.token = token;
            this.secret = secret;
            this.recoveryCodes = recoveryCodes;
        }
    }

    private UserContext createMfaEnabledUser() throws Exception {
        String email = "mfa_sec_" + UUID.randomUUID() + "@example.com";
        String password = "Password123!" + UUID.randomUUID().toString().substring(0, 8);
        String ip = nextClientIp();

        // Register
        RegisterRequest registerReq = new RegisterRequest(email, password, "Security User", "Security Org");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // Enroll
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("X-Forwarded-For", ip)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String secret = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .path("data").path("secret").asText();

        // Activate
        String code = totpService.generateCode(secret);
        MvcResult actResult = mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("X-Forwarded-For", ip)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaActivateRequest(code))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> recoveryCodes = new ArrayList<>();
        for (JsonNode n : objectMapper.readTree(actResult.getResponse().getContentAsString()).path("data").path("recoveryCodes")) {
            recoveryCodes.add(n.asText());
        }

        return new UserContext(email, password, token, secret, recoveryCodes);
    }

    @Test
    @DisplayName("Primary Invariant: Password validation for MFA user issues ZERO tokens until secondary factor is verified")
    void testPrimarySecurityInvariantNoTokenLeakage() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        String ip = nextClientIp();

        // Login with password
        LoginRequest loginReq = new LoginRequest(ctx.email, ctx.password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.mfaChallengeId").isString())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.user").doesNotExist())
                .andReturn();

        // Attempting to use a null/missing token on protected endpoint MUST return 401
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Adversarial: Client cannot bypass MFA by injecting mfaVerified=true or state=AUTHENTICATED")
    void testClientControlledStateInjectionRejected() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        String ip = nextClientIp();

        // Inject forged client parameters into login request
        String maliciousJson = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"mfaVerified\":true,\"state\":\"AUTHENTICATED\",\"mfaRequired\":false}",
                ctx.email, ctx.password
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("Adversarial: Cross-user challenge theft (User B attempting to verify User A challenge) must fail")
    void testCrossUserChallengeTheftRejected() throws Exception {
        UserContext userA = createMfaEnabledUser();
        UserContext userB = createMfaEnabledUser();
        String ip = nextClientIp();

        // User A gets challenge
        LoginRequest loginA = new LoginRequest(userA.email, userA.password);
        MvcResult resA = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginA)))
                .andExpect(status().isOk())
                .andReturn();

        String challengeA = objectMapper.readTree(resA.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        // User B generates TOTP using User B's secret and tries to verify User A's challenge
        String codeB = totpService.generateCode(userB.secret);
        MfaTotpVerifyRequest stealReq = new MfaTotpVerifyRequest(challengeA, codeB);

        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stealReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Adversarial: High-concurrency challenge verification (25 parallel threads) allows EXACTLY ONE success")
    void testConcurrentChallengeVerificationSingleWinner() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        String loginIp = nextClientIp();

        // Get challenge
        LoginRequest loginReq = new LoginRequest(ctx.email, ctx.password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", loginIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();
        String validCode = totpService.generateCode(ctx.secret);

        int threadCount = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String clientIp = "172.20." + ((i / 250) + 1) + "." + ((i % 250) + 1);
            tasks.add(() -> {
                startLatch.await();
                MfaTotpVerifyRequest req = new MfaTotpVerifyRequest(challengeId, validCode);
                try {
                    MvcResult mvcRes = mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                                    .header("X-Forwarded-For", clientIp)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    if (mvcRes.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        startLatch.countDown();
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "EXACTLY ONE concurrent verification MUST succeed");
        assertEquals(threadCount - 1, failureCount.get(), "All other concurrent attempts MUST fail");
    }

    @Test
    @DisplayName("Adversarial: High-concurrency recovery code verification (25 parallel threads) allows EXACTLY ONE success")
    void testConcurrentRecoveryCodeVerificationSingleWinner() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        String recoveryCode = ctx.recoveryCodes.get(0);
        String loginIp = nextClientIp();

        // Get challenge
        LoginRequest loginReq = new LoginRequest(ctx.email, ctx.password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", loginIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        int threadCount = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String clientIp = "172.21." + ((i / 250) + 1) + "." + ((i % 250) + 1);
            tasks.add(() -> {
                startLatch.await();
                MfaRecoveryVerifyRequest req = new MfaRecoveryVerifyRequest(challengeId, recoveryCode);
                try {
                    MvcResult mvcRes = mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                                    .header("X-Forwarded-For", clientIp)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    if (mvcRes.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        startLatch.countDown();
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "EXACTLY ONE concurrent recovery verification MUST succeed");
        assertEquals(threadCount - 1, failureCount.get(), "All other concurrent attempts MUST fail");
    }

    @Test
    @DisplayName("Adversarial: Exhausting all 10 recovery codes leaves MFA enabled and rejects further recovery attempts")
    void testRecoveryCodeExhaustionLeavesMfaActive() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        LoginRequest loginReq = new LoginRequest(ctx.email, ctx.password);

        // Use all 10 recovery codes sequentially
        for (int i = 0; i < 10; i++) {
            String ip = nextClientIp();
            MvcResult chalRes = mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            String challengeId = objectMapper.readTree(chalRes.getResponse().getContentAsString())
                    .path("data").path("mfaChallengeId").asText();

            MfaRecoveryVerifyRequest recReq = new MfaRecoveryVerifyRequest(challengeId, ctx.recoveryCodes.get(i));
            mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(recReq)))
                    .andExpect(status().isOk());
        }

        // 11th attempt with used recovery code -> MUST FAIL
        String ip11 = nextClientIp();
        MvcResult chalRes11 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip11)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId11 = objectMapper.readTree(chalRes11.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        MfaRecoveryVerifyRequest reusedReq = new MfaRecoveryVerifyRequest(challengeId11, ctx.recoveryCodes.get(0));
        mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                        .header("X-Forwarded-For", ip11)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reusedReq)))
                .andExpect(status().isUnauthorized());

        // TOTP code still works
        String validTotp = totpService.generateCode(ctx.secret);
        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", ip11)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaTotpVerifyRequest(challengeId11, validTotp))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Adversarial: TOTP clock drift is strictly bounded (past/future ±30s allowed, ±90s rejected)")
    void testTotpClockDriftBounds() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        LoginRequest loginReq = new LoginRequest(ctx.email, ctx.password);

        // 1. Valid at t0
        String ip1 = nextClientIp();
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk()).andReturn();
        String chal1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("mfaChallengeId").asText();
        String codeT0 = totpService.generateCode(ctx.secret, Instant.now());
        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", ip1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaTotpVerifyRequest(chal1, codeT0))))
                .andExpect(status().isOk());

        // 2. Valid at t-30s (drift step -1)
        String ip2 = nextClientIp();
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk()).andReturn();
        String chal2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("mfaChallengeId").asText();
        String codePast = totpService.generateCode(ctx.secret, Instant.now().minusSeconds(30));
        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", ip2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaTotpVerifyRequest(chal2, codePast))))
                .andExpect(status().isOk());

        // 3. Rejected at t+90s (drift step +3, exceeding allowed tolerance)
        String ip3 = nextClientIp();
        MvcResult res3 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk()).andReturn();
        String chal3 = objectMapper.readTree(res3.getResponse().getContentAsString()).path("data").path("mfaChallengeId").asText();
        String codeFarFuture = totpService.generateCode(ctx.secret, Instant.now().plusSeconds(90));
        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", ip3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaTotpVerifyRequest(chal3, codeFarFuture))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Adversarial: Stale MFA challenge is rejected after MFA is disabled")
    void testStaleChallengeRejectedAfterMfaDisabled() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        String ip = nextClientIp();

        // 1. Create challenge while MFA is enabled
        LoginRequest loginReq = new LoginRequest(ctx.email, ctx.password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk()).andReturn();

        String staleChallengeId = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("mfaChallengeId").asText();

        // 2. Disable MFA with step-up credentials
        String code = totpService.generateCode(ctx.secret);
        MfaDisableRequest disableReq = new MfaDisableRequest(ctx.password, code, null);
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("X-Forwarded-For", ip)
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableReq)))
                .andExpect(status().isOk());

        // 3. Attempt to submit stale challenge with valid code -> MUST BE REJECTED
        MfaTotpVerifyRequest staleReq = new MfaTotpVerifyRequest(staleChallengeId, code);
        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Adversarial: Deactivating account invalidates active MFA challenge")
    void testAccountDeactivationInvalidatesActiveChallenge() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        String ip = nextClientIp();

        // 1. Create login challenge
        LoginRequest loginReq = new LoginRequest(ctx.email, ctx.password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk()).andReturn();

        String challengeId = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("mfaChallengeId").asText();

        // 2. Suspend/disable user account in database
        User user = userRepository.findByEmail(ctx.email).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        // 3. Attempt to verify challenge with valid TOTP -> MUST FAIL with 401
        String code = totpService.generateCode(ctx.secret);
        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaTotpVerifyRequest(challengeId, code))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rate Limiting: Exceeding verification rate limit triggers 429 Too Many Requests")
    void testRateLimiterTriggers429OnIpAbuse() throws Exception {
        String abuseIp = "198.51.100.99";
        MfaTotpVerifyRequest req = new MfaTotpVerifyRequest(UUID.randomUUID().toString(), "123456");

        // 10 requests allowed
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                            .header("X-Forwarded-For", abuseIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        // 11th request triggers 429
        mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", abuseIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Lifecycle: Comprehensive full MFA lifecycle, recovery code reuse prevention, step-up disable, and clean re-enrollment")
    void testFullMfaLifecycleAndReEnrollment() throws Exception {
        String email = "lifecycle_" + UUID.randomUUID() + "@example.com";
        String password = "Password123!Secure";
        String ip = nextClientIp();

        // 1. Register account
        RegisterRequest registerReq = new RegisterRequest(email, password, "Lifecycle User", "Lifecycle Org");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token1 = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. Initial Login without MFA -> Returns tokens directly
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult initialLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(false))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        // 3. Enroll in MFA
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("X-Forwarded-For", nextClientIp())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andReturn();

        String secret1 = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .path("data").path("secret").asText();

        // 4. Activate MFA
        String code1 = totpService.generateCode(secret1);
        MvcResult actResult = mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("X-Forwarded-For", nextClientIp())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaActivateRequest(code1))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> recoveryCodes1 = new ArrayList<>();
        for (JsonNode n : objectMapper.readTree(actResult.getResponse().getContentAsString()).path("data").path("recoveryCodes")) {
            recoveryCodes1.add(n.asText());
        }
        assertEquals(10, recoveryCodes1.size());

        // 5. Login again -> MFA Required
        MvcResult loginMfaRes1 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andReturn();

        String chal1 = objectMapper.readTree(loginMfaRes1.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        // 6. Complete login with TOTP
        String loginTotp = totpService.generateCode(secret1);
        MvcResult totpVerifyRes = mockMvc.perform(post("/api/v1/auth/mfa/verify-totp")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaTotpVerifyRequest(chal1, loginTotp))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String token2 = objectMapper.readTree(totpVerifyRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 7. Login again with recovery code
        MvcResult loginMfaRes2 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String chal2 = objectMapper.readTree(loginMfaRes2.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        String usedRecoveryCode = recoveryCodes1.get(0);
        mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaRecoveryVerifyRequest(chal2, usedRecoveryCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString());

        // 8. Attempt recovery code reuse on new challenge -> MUST FAIL
        MvcResult loginMfaRes3 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String chal3 = objectMapper.readTree(loginMfaRes3.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaRecoveryVerifyRequest(chal3, usedRecoveryCode))))
                .andExpect(status().isUnauthorized());

        // 9. Step-up disable MFA
        String disableTotp = totpService.generateCode(secret1);
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("X-Forwarded-For", nextClientIp())
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaDisableRequest(password, disableTotp, null))))
                .andExpect(status().isOk());

        // 10. Login after disable -> Immediate token issuance (no MFA challenge)
        MvcResult postDisableLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(false))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String token3 = objectMapper.readTree(postDisableLogin.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 11. Re-enroll and activate with NEW secret
        MvcResult reEnrollRes = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("X-Forwarded-For", nextClientIp())
                        .header("Authorization", "Bearer " + token3))
                .andExpect(status().isOk())
                .andReturn();

        String secret2 = objectMapper.readTree(reEnrollRes.getResponse().getContentAsString())
                .path("data").path("secret").asText();
        assertNotEquals(secret1, secret2, "New enrollment MUST generate a fresh TOTP secret");

        String code2 = totpService.generateCode(secret2);
        MvcResult reActRes = mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("X-Forwarded-For", nextClientIp())
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaActivateRequest(code2))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> recoveryCodes2 = new ArrayList<>();
        for (JsonNode n : objectMapper.readTree(reActRes.getResponse().getContentAsString()).path("data").path("recoveryCodes")) {
            recoveryCodes2.add(n.asText());
        }

        // 12. Verify that OLD recovery codes from previous enrollment cannot authenticate
        MvcResult loginMfaRes4 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String chal4 = objectMapper.readTree(loginMfaRes4.getResponse().getContentAsString())
                .path("data").path("mfaChallengeId").asText();

        // Old recovery code #1 from first enrollment -> MUST FAIL
        mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaRecoveryVerifyRequest(chal4, recoveryCodes1.get(1)))))
                .andExpect(status().isUnauthorized());

        // New recovery code #0 from re-enrollment -> MUST SUCCEED
        mockMvc.perform(post("/api/v1/auth/mfa/verify-recovery")
                        .header("X-Forwarded-For", nextClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaRecoveryVerifyRequest(chal4, recoveryCodes2.get(0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString());
    }

    @Test
    @DisplayName("IDOR: Authenticated user cannot manage or query another user's MFA state")
    void testIdorProtectionOnMfaEndpoints() throws Exception {
        UserContext userA = createMfaEnabledUser();
        UserContext userB = createMfaEnabledUser();

        // User A's token accessing /mfa/status returns User A's state only
        MvcResult statusA = mockMvc.perform(get("/api/v1/auth/mfa/status")
                        .header("X-Forwarded-For", nextClientIp())
                        .header("Authorization", "Bearer " + userA.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn();

        // User A attempting to disable MFA using User B's password -> Fails
        String codeA = totpService.generateCode(userA.secret);
        MfaDisableRequest crossDisable = new MfaDisableRequest(userB.password, codeA, null);
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("X-Forwarded-For", nextClientIp())
                        .header("Authorization", "Bearer " + userA.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossDisable)))
                .andExpect(status().isUnauthorized());

        // Verify User B's MFA remains enabled
        mockMvc.perform(get("/api/v1/auth/mfa/status")
                        .header("X-Forwarded-For", nextClientIp())
                        .header("Authorization", "Bearer " + userB.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @DisplayName("HTTP Security: All MFA sensitive endpoints return Cache-Control: no-store")
    void testHttpCacheControlNoStore() throws Exception {
        UserContext ctx = createMfaEnabledUser();
        String ip = nextClientIp();

        mockMvc.perform(get("/api/v1/auth/mfa/status")
                        .header("X-Forwarded-For", ip)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("X-Forwarded-For", ip)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }
}
