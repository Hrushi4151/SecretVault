package com.secretvault.auth.webauthn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.security.JwtTokenProvider;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.auth.webauthn.config.WebAuthnProperties;
import com.secretvault.auth.webauthn.model.WebAuthnAuthenticationChallengePayload;
import com.secretvault.auth.webauthn.model.WebAuthnCeremonyType;
import com.secretvault.auth.webauthn.repository.UserWebAuthnCredentialRepository;
import com.secretvault.auth.webauthn.service.DefaultWebAuthnService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import com.yubico.webauthn.RelyingParty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebAuthnConcurrencyTest {

    @Mock
    private RelyingParty relyingParty;

    @Mock
    private WebAuthnProperties properties;

    @Mock
    private UserWebAuthnCredentialRepository credentialRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private SecurityStateStore securityStateStore;

    @Mock
    private SessionService sessionService;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuditService auditService;

    @Mock
    private MfaService mfaService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DefaultWebAuthnService webAuthnService;

    private String challengeId;
    private WebAuthnAuthenticationChallengePayload challengePayload;

    @BeforeEach
    void setUp() {
        challengeId = "webauthn_concurrent_chlg_123";
        challengePayload = WebAuthnAuthenticationChallengePayload.of(
                challengeId,
                UUID.randomUUID(),
                "sess_123",
                WebAuthnCeremonyType.LOGIN,
                "{\"mock\":\"request\"}",
                null,
                null,
                300
        );
    }

    @Test
    @DisplayName("CONCURRENCY: Exactly 1 out of 16 concurrent threads consumes WebAuthn authentication challenge")
    void testConcurrentChallengeConsumption_16Threads() throws InterruptedException {
        AtomicBoolean consumedFlag = new AtomicBoolean(false);

        when(securityStateStore.consumeAtomic(
                eq("webauthn_auth_challenge"),
                eq(challengeId),
                eq(WebAuthnAuthenticationChallengePayload.class)
        )).thenAnswer(invocation -> {
            if (consumedFlag.compareAndSet(false, true)) {
                return Optional.of(challengePayload);
            }
            return Optional.empty();
        });

        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        AtomicInteger consumedWinnerCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    // When finishAuthentication runs, it consumes the challenge
                    webAuthnService.finishAuthentication(challengeId, "{}");
                    consumedWinnerCount.incrementAndGet();
                } catch (ApiException ex) {
                    if ("WEBAUTHN_CHALLENGE_INVALID".equals(ex.getCode())) {
                        rejectedCount.incrementAndGet();
                    } else {
                        // The winner may fail at subsequent JSON parsing mock, but it was the sole consumer!
                        consumedWinnerCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    consumedWinnerCount.incrementAndGet();
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        finishGate.await();
        executor.shutdown();

        assertEquals(1, consumedWinnerCount.get(), "Exactly one concurrent thread must consume the WebAuthn challenge");
        assertEquals(threadCount - 1, rejectedCount.get(), "All 15 other concurrent requests must be rejected with challenge invalid/replay");
    }
}
