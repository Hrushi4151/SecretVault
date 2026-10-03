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
}
