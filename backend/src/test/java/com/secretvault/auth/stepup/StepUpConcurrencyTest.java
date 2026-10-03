package com.secretvault.auth.stepup;

import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.auth.stepup.model.StepUpFactor;
import com.secretvault.auth.stepup.model.StepUpProofPayload;
import com.secretvault.auth.stepup.service.DefaultStepUpAuthenticationService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
class StepUpConcurrencyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private SecurityStateStore securityStateStore;

    @Mock
    private AuditService auditService;

    @Mock
    private com.secretvault.auth.mfa.service.MfaService mfaService;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private com.secretvault.auth.webauthn.repository.UserWebAuthnCredentialRepository webAuthnCredentialRepository;

    @Mock
    private com.secretvault.auth.webauthn.service.WebAuthnService webAuthnService;

    private DefaultStepUpAuthenticationService stepUpService;

    private UUID userId;
    private String sessionIdentifier;
    private UserSession activeSession;
    private StepUpContext context;

    @BeforeEach
    void setUp() {
        stepUpService = new DefaultStepUpAuthenticationService(
                userRepository,
                sessionRepository,
                mfaService,
                securityStateStore,
                auditService,
                passwordEncoder,
                null,
                webAuthnCredentialRepository,
                webAuthnService
        );
        userId = UUID.randomUUID();
        sessionIdentifier = "sess_concurrency_test_123456";
        activeSession = new UserSession(userId, sessionIdentifier, AuthMethod.PASSWORD, "127.0.0.1", "Browser", "Device", "Chrome", "Linux", Instant.now().plusSeconds(3600));
        context = StepUpContext.forSecret(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("CONCURRENCY: Exactly one thread succeeds in consuming a single-use proof token")
    void testConcurrentProofConsumption_singleWinner() throws InterruptedException {
        String proofToken = "stup_concurrent_token_xyz";
        StepUpProofPayload proof = StepUpProofPayload.of(
                proofToken, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, context, StepUpFactor.PASSWORD, 300
        );

        // Atomic simulation of Lua atomic GET-and-DEL in Redis
        AtomicBoolean consumedFlag = new AtomicBoolean(false);
        when(securityStateStore.consumeAtomic(eq("step_up_proof"), eq(proofToken), eq(StepUpProofPayload.class)))
                .thenAnswer(invocation -> {
                    if (consumedFlag.compareAndSet(false, true)) {
                        return Optional.of(proof);
                    }
                    return Optional.empty();
                });

        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    stepUpService.verifyAndConsumeProof(proofToken, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, context);
                    successCount.incrementAndGet();
                } catch (ApiException ex) {
                    if ("STEP_UP_INVALID".equals(ex.getCode())) {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        finishGate.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in consuming the proof");
        assertEquals(threadCount - 1, rejectedCount.get(), "All other concurrent attempts must be rejected with replay/invalidation");
    }
}
