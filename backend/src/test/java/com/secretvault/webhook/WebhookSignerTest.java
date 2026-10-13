package com.secretvault.webhook;

import com.secretvault.webhook.security.WebhookSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 13: Webhook HMAC-SHA256 Signer Tests")
class WebhookSignerTest {

    private WebhookSigner signer;

    @BeforeEach
    void setUp() {
        signer = new WebhookSigner();
    }

    @Test
    @DisplayName("Generate signing secret with whsec_ prefix and high entropy")
    void testGenerateSecret() {
        String secret = signer.generateSigningSecret();
        assertThat(secret).startsWith("whsec_");
        assertThat(secret).hasSize(70); // "whsec_" (6) + 64 hex chars (32 bytes)
    }

    @Test
    @DisplayName("Signature compute and verify roundtrip succeeds within tolerance window")
    void testComputeAndVerify() {
        String secret = signer.generateSigningSecret();
        long now = System.currentTimeMillis() / 1000;
        String payload = "{\"eventType\":\"SECRET_ROTATED\",\"workspaceId\":\"test\"}";

        String signatureHeader = signer.computeSignature(secret, now, payload);
        assertThat(signatureHeader).contains("t=" + now);
        assertThat(signatureHeader).contains(",v1=");

        boolean valid = signer.verifySignature(secret, signatureHeader, payload, 300);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Verification fails if payload was tampered")
    void testTamperedPayload() {
        String secret = signer.generateSigningSecret();
        long now = System.currentTimeMillis() / 1000;
        String payload = "{\"eventType\":\"SECRET_ROTATED\"}";
        String signatureHeader = signer.computeSignature(secret, now, payload);

        String tamperedPayload = "{\"eventType\":\"SECRET_COMPROMISED\"}";
        boolean valid = signer.verifySignature(secret, signatureHeader, tamperedPayload, 300);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Verification fails if timestamp is outside tolerance window (replay attack defense)")
    void testExpiredTimestamp() {
        String secret = signer.generateSigningSecret();
        long oldTimestamp = (System.currentTimeMillis() / 1000) - 1000; // 1000s ago
        String payload = "{\"eventType\":\"SECRET_ROTATED\"}";
        String signatureHeader = signer.computeSignature(secret, oldTimestamp, payload);

        boolean valid = signer.verifySignature(secret, signatureHeader, payload, 300); // 300s tolerance
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Fail-closed on null or blank signing secret")
    void testFailClosedOnEmptySecret() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                signer.computeSignature(null, 123456789L, "{}")
        );
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                signer.computeSignature("   ", 123456789L, "{}")
        );
        assertThat(signer.verifySignature(null, "t=1,v1=abc", "{}", 300)).isFalse();
        assertThat(signer.verifySignature("", "t=1,v1=abc", "{}", 300)).isFalse();
    }

    @Test
    @DisplayName("Dual-secret rotation verification supports graceful transition within grace period")
    void testDualSecretRotationVerification() {
        String oldSecret = signer.generateSigningSecret();
        String newSecret = signer.generateSigningSecret();
        long now = System.currentTimeMillis() / 1000;
        String payload = "{\"eventType\":\"SECRET_ACCESSED\"}";

        String signatureWithOld = signer.computeSignature(oldSecret, now, payload);
        String signatureWithNew = signer.computeSignature(newSecret, now, payload);

        java.time.Instant rotatedRecently = java.time.Instant.now().minusSeconds(3600); // 1 hour ago
        java.time.Instant rotatedLongAgo = java.time.Instant.now().minusSeconds(100000); // > 24 hours ago

        // 1. New secret always verifies
        assertThat(signer.verifySignatureWithDualSecret(newSecret, oldSecret, rotatedRecently, signatureWithNew, payload, 300, 86400)).isTrue();

        // 2. Old secret verifies if rotated recently (within 24h grace period)
        assertThat(signer.verifySignatureWithDualSecret(newSecret, oldSecret, rotatedRecently, signatureWithOld, payload, 300, 86400)).isTrue();

        // 3. Old secret rejected if grace period expired
        assertThat(signer.verifySignatureWithDualSecret(newSecret, oldSecret, rotatedLongAgo, signatureWithOld, payload, 300, 86400)).isFalse();

        // 4. Completely invalid signature rejected
        assertThat(signer.verifySignatureWithDualSecret(newSecret, oldSecret, rotatedRecently, "t=" + now + ",v1=deadbeef", payload, 300, 86400)).isFalse();
    }
}
