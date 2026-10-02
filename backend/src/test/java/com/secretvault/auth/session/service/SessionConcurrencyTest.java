package com.secretvault.auth.session.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.RefreshTokenRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SessionConcurrencyTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Concurrency: Two simultaneous refreshes with same token -> exactly one succeeds, one fails replay")
    void testConcurrentRefreshSameToken() throws Exception {
        String email = "concurrency_refresh_" + UUID.randomUUID() + "@example.com";
        AuthResponse registered = authService.register(new RegisterRequest(email, "Password123!Secure", "Conc User", "Conc Org"));
        String rawRefreshToken = registered.refreshToken();

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                latch.await();
                try {
                    authService.refresh(new RefreshTokenRequest(rawRefreshToken));
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (var task : tasks) {
            futures.add(executor.submit(task));
        }

        latch.countDown();
        for (var f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Strict token rotation must allow exactly ONE refresh exchange");
        assertEquals(threads - 1, failureCount.get(), "All other simultaneous refreshes must fail replay check");
    }

    @Test
    @DisplayName("Concurrency: Concurrent session revocations are safe and idempotent")
    void testConcurrentSessionRevocation() throws Exception {
        String email = "concurrency_revoke_" + UUID.randomUUID() + "@example.com";
        AuthResponse registered = authService.register(new RegisterRequest(email, "Password123!Secure", "Conc User", "Conc Org"));
        var sessions = sessionService.listUserSessions(registered.user().id(), null);
        String sessionId = sessions.get(0).id();

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                latch.await();
                try {
                    sessionService.revokeSession(registered.user().id(), sessionId);
                } catch (Exception ex) {
                    errors.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (var task : tasks) {
            futures.add(executor.submit(task));
        }

        latch.countDown();
        for (var f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(0, errors.get(), "Concurrent revocations must complete idempotently without throwing errors");
        var updated = sessionService.findByIdentifier(sessionId).orElseThrow();
        assertTrue(updated.isRevoked(), "Session must be revoked");
    }

    @Test
    @DisplayName("Concurrency: Two independent browser sessions refresh simultaneously without conflict")
    void testTwoIndependentSessionsConcurrentRefresh() throws Exception {
        String email = "concurrency_dual_" + UUID.randomUUID() + "@example.com";
        AuthResponse session1 = authService.register(new RegisterRequest(email, "Password123!Secure", "Dual User", "Dual Org"));
        AuthResponse session2 = authService.login(new LoginRequest(email, "Password123!Secure"));

        assertNotEquals(session1.refreshToken(), session2.refreshToken());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<AuthResponse> future1 = executor.submit(() -> {
            latch.await();
            return authService.refresh(new RefreshTokenRequest(session1.refreshToken()));
        });

        Future<AuthResponse> future2 = executor.submit(() -> {
            latch.await();
            return authService.refresh(new RefreshTokenRequest(session2.refreshToken()));
        });

        latch.countDown();

        AuthResponse refreshed1 = future1.get();
        AuthResponse refreshed2 = future2.get();
        executor.shutdown();

        assertNotNull(refreshed1);
        assertNotNull(refreshed2);
        assertNotNull(refreshed1.accessToken());
        assertNotNull(refreshed2.accessToken());
    }

    @Test
    @DisplayName("Concurrency: Revoking session A leaves session B active and refreshable")
    void testRevokeSessionALeavesBActive() throws Exception {
        String email = "concurrency_ab_" + UUID.randomUUID() + "@example.com";
        AuthResponse sessionA = authService.register(new RegisterRequest(email, "Password123!Secure", "AB User", "AB Org"));
        AuthResponse sessionB = authService.login(new LoginRequest(email, "Password123!Secure"));

        var sessions = sessionService.listUserSessions(sessionA.user().id(), null);
        assertEquals(2, sessions.size());

        // Revoke Session A
        sessionService.revokeSession(sessionA.user().id(), sessions.get(1).id());

        // Session A refresh fails
        assertThrows(Exception.class, () ->
                authService.refresh(new RefreshTokenRequest(sessionA.refreshToken())));

        // Session B refresh succeeds
        AuthResponse refreshedB = authService.refresh(new RefreshTokenRequest(sessionB.refreshToken()));
        assertNotNull(refreshedB);
        assertNotNull(refreshedB.accessToken());
    }
}
