package com.secretvault.webhook.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class WebhookSigner {

    private static final Logger log = LoggerFactory.getLogger(WebhookSigner.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateSigningSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    public String computeSignature(String signingSecret, long timestampSeconds, String rawPayload) {
        try {
            String dataToSign = timestampSeconds + "." + (rawPayload != null ? rawPayload : "");
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            return "t=" + timestampSeconds + ",v1=" + HexFormat.of().formatHex(rawHmac);
        } catch (Exception ex) {
            log.error("Failed to compute webhook signature: {}", ex.getMessage(), ex);
            throw new RuntimeException("Webhook signing error", ex);
        }
    }

    public boolean verifySignature(String signingSecret, String headerSignature, String rawPayload, long toleranceSeconds) {
        if (headerSignature == null || !headerSignature.contains("t=") || !headerSignature.contains("v1=")) {
            return false;
        }

        try {
            String[] parts = headerSignature.split(",");
            long timestamp = 0;
            String signature = null;

            for (String part : parts) {
                if (part.startsWith("t=")) {
                    timestamp = Long.parseLong(part.substring(2));
                } else if (part.startsWith("v1=")) {
                    signature = part.substring(3);
                }
            }

            if (timestamp == 0 || signature == null) return false;

            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - timestamp) > toleranceSeconds) {
                log.warn("Webhook signature expired: delta is {}s (tolerance {}s)", Math.abs(now - timestamp), toleranceSeconds);
                return false;
            }

            String expectedHeader = computeSignature(signingSecret, timestamp, rawPayload);
            String expectedSig = expectedHeader.substring(expectedHeader.indexOf("v1=") + 3);

            return MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSig.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
