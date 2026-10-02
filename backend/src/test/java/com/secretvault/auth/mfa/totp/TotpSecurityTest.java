package com.secretvault.auth.mfa.totp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TOTP Engine Security Invariant & Concurrency Tests")
class TotpSecurityTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        TotpProperties properties = new TotpProperties();
        totpService = new TotpService(properties, Clock.systemUTC());
    }

    @Test
    @DisplayName("Exception messages never leak secret content")
    void testExceptionMessageSanitization() {
        String sensitiveSecret = "MYSECRETKEYTHATSHOULDNEVERAPPEARINLOGS";

        // When invalid input is supplied to Base32 decode
        assertThatThrownBy(() -> Base32.decode(sensitiveSecret + "8888"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(sensitiveSecret);

        // When invalid secret is supplied to provisioning URI builder
        assertThatThrownBy(() -> totpService.buildProvisioningUri(sensitiveSecret + "8888", "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(sensitiveSecret);
    }

    @Test
    @DisplayName("TOTP generation and verification is thread-safe under concurrent load")
    void testConcurrentTotpOperations() throws Exception {
        int threadCount = 50;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String secret = totpService.generateSecret();
                        Instant now = Instant.now();
                        String code = totpService.generateCode(secret, now);
                        boolean valid = totpService.verifyCode(secret, code, now, 1);
                        if (!valid) {
                            return false;
                        }
                    }
                    return true;
                });
            }

            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                assertThat(future.get()).isTrue();
            }
        } finally {
            executor.shutdown();
        }
    }
}
