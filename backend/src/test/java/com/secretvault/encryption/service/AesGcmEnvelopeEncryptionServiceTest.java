package com.secretvault.encryption.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.kms.LocalDevKmsKeyProvider;
import com.secretvault.encryption.model.EncryptedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AES-256-GCM Envelope Encryption Service Tests")
class AesGcmEnvelopeEncryptionServiceTest {

    // 256-bit test master key (32 bytes)
    private static final String TEST_KEK_BASE64 = Base64.getEncoder().encodeToString(
            "test_master_key_32_bytes_dev_001".getBytes(StandardCharsets.UTF_8)
    );

    // Alternative 256-bit test master key (32 bytes)
    private static final String WRONG_KEK_BASE64 = Base64.getEncoder().encodeToString(
            "wrong_master_key_32_bytes_dev_99".getBytes(StandardCharsets.UTF_8)
    );

    private LocalDevKmsKeyProvider kmsKeyProvider;
    private AesGcmEnvelopeEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        kmsKeyProvider = new LocalDevKmsKeyProvider(TEST_KEK_BASE64);
        kmsKeyProvider.init();
        encryptionService = new AesGcmEnvelopeEncryptionService(kmsKeyProvider);
    }

    @Test
    @DisplayName("1. Encrypt and decrypt roundtrip matches original plaintext")
    void testEncryptDecryptRoundTrip() {
        String secretPlaintext = "super-sensitive-production-api-token-12345!@#$%^&*()_+";
        String aad = "sec-uuid-1:env-uuid-1:1";

        EncryptedPayload payload = encryptionService.encrypt(
                secretPlaintext.getBytes(StandardCharsets.UTF_8),
                aad
        );

        assertThat(payload).isNotNull();
        assertThat(payload.ciphertext()).isNotEmpty();
        assertThat(payload.encryptedDek()).isNotEmpty();
        assertThat(payload.iv()).hasSize(12);
        assertThat(payload.authTag()).hasSize(16);
        assertThat(payload.keyReference()).isEqualTo("local-dev-kek-v1");

        byte[] decrypted = encryptionService.decrypt(payload, aad);
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(secretPlaintext);
    }

    @Test
    @DisplayName("2. Fresh random 256-bit DEK generated for each encryption operation")
    void testFreshDekPerEncryption() {
        byte[] plaintext = "identical-secret-payload".getBytes(StandardCharsets.UTF_8);
        String aad = "sec-1:env-1:1";

        EncryptedPayload payload1 = encryptionService.encrypt(plaintext, aad);
        EncryptedPayload payload2 = encryptionService.encrypt(plaintext, aad);

        assertThat(payload1.encryptedDek()).isNotEqualTo(payload2.encryptedDek());
    }

    @Test
    @DisplayName("3. Fresh random 96-bit IV generated for each encryption operation")
    void testFreshIvPerEncryption() {
        byte[] plaintext = "identical-secret-payload".getBytes(StandardCharsets.UTF_8);
        String aad = "sec-1:env-1:1";

        EncryptedPayload payload1 = encryptionService.encrypt(plaintext, aad);
        EncryptedPayload payload2 = encryptionService.encrypt(plaintext, aad);

        assertThat(payload1.iv()).isNotEqualTo(payload2.iv());
    }

    @Test
    @DisplayName("4. Same plaintext produces completely different ciphertext across calls")
    void testCiphertextUniqueness() {
        byte[] plaintext = "database-password-prod".getBytes(StandardCharsets.UTF_8);
        String aad = "sec-1:env-1:1";

        EncryptedPayload payload1 = encryptionService.encrypt(plaintext, aad);
        EncryptedPayload payload2 = encryptionService.encrypt(plaintext, aad);

        assertThat(payload1.ciphertext()).isNotEqualTo(payload2.ciphertext());
    }

    @Test
    @DisplayName("5. Tampered ciphertext is rejected with SECRET_DECRYPTION_FAILED")
    void testTamperedCiphertextRejected() {
        byte[] plaintext = "confidential".getBytes(StandardCharsets.UTF_8);
        String aad = "sec-1:env-1:1";

        EncryptedPayload payload = encryptionService.encrypt(plaintext, aad);

        byte[] corruptedCiphertext = Arrays.copyOf(payload.ciphertext(), payload.ciphertext().length);
        corruptedCiphertext[0] ^= 0xFF; // Flip bit

        EncryptedPayload tampered = new EncryptedPayload(
                corruptedCiphertext,
                payload.encryptedDek(),
                payload.iv(),
                payload.authTag(),
                payload.keyReference()
        );

        assertThatThrownBy(() -> encryptionService.decrypt(tampered, aad))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SECRET_DECRYPTION_FAILED"));
    }

    @Test
    @DisplayName("6. Tampered IV is rejected with SECRET_DECRYPTION_FAILED")
    void testTamperedIvRejected() {
        byte[] plaintext = "confidential".getBytes(StandardCharsets.UTF_8);
        String aad = "sec-1:env-1:1";

        EncryptedPayload payload = encryptionService.encrypt(plaintext, aad);

        byte[] corruptedIv = Arrays.copyOf(payload.iv(), payload.iv().length);
        corruptedIv[0] ^= 0x5A;

        EncryptedPayload tampered = new EncryptedPayload(
                payload.ciphertext(),
                payload.encryptedDek(),
                corruptedIv,
                payload.authTag(),
                payload.keyReference()
        );

        assertThatThrownBy(() -> encryptionService.decrypt(tampered, aad))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SECRET_DECRYPTION_FAILED"));
    }

    @Test
    @DisplayName("7. Tampered authentication tag is rejected")
    void testTamperedAuthTagRejected() {
        byte[] plaintext = "confidential".getBytes(StandardCharsets.UTF_8);
        String aad = "sec-1:env-1:1";

        EncryptedPayload payload = encryptionService.encrypt(plaintext, aad);

        byte[] corruptedTag = Arrays.copyOf(payload.authTag(), payload.authTag().length);
        corruptedTag[15] ^= 0x01;

        EncryptedPayload tampered = new EncryptedPayload(
                payload.ciphertext(),
                payload.encryptedDek(),
                payload.iv(),
                corruptedTag,
                payload.keyReference()
        );

        assertThatThrownBy(() -> encryptionService.decrypt(tampered, aad))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SECRET_DECRYPTION_FAILED"));
    }

    @Test
    @DisplayName("8. Tampered wrapped DEK is rejected")
    void testTamperedWrappedDekRejected() {
        byte[] plaintext = "confidential".getBytes(StandardCharsets.UTF_8);
        String aad = "sec-1:env-1:1";

        EncryptedPayload payload = encryptionService.encrypt(plaintext, aad);

        byte[] corruptedDek = Arrays.copyOf(payload.encryptedDek(), payload.encryptedDek().length);
        corruptedDek[0] ^= 0x33;

        EncryptedPayload tampered = new EncryptedPayload(
                payload.ciphertext(),
                corruptedDek,
                payload.iv(),
                payload.authTag(),
                payload.keyReference()
        );

        assertThatThrownBy(() -> encryptionService.decrypt(tampered, aad))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SECRET_DECRYPTION_FAILED"));
    }

    @Test
    @DisplayName("9. Decryption with a different KEK fails closed")
    void testWrongKekRejected() {
        byte[] plaintext = "confidential".getBytes(StandardCharsets.UTF_8);
        String aad = "sec-1:env-1:1";

        EncryptedPayload payload = encryptionService.encrypt(plaintext, aad);

        LocalDevKmsKeyProvider wrongKms = new LocalDevKmsKeyProvider(WRONG_KEK_BASE64);
        wrongKms.init();
        AesGcmEnvelopeEncryptionService wrongService = new AesGcmEnvelopeEncryptionService(wrongKms);

        assertThatThrownBy(() -> wrongService.decrypt(payload, aad))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SECRET_DECRYPTION_FAILED"));
    }

    @Test
    @DisplayName("10. Wrong Authenticated Additional Data (AAD) context fails decryption")
    void testWrongAadContextRejected() {
        byte[] plaintext = "confidential".getBytes(StandardCharsets.UTF_8);
        String correctAad = "secret-100:env-200:1";
        String attackerSwappedAad = "secret-999:env-888:1";

        EncryptedPayload payload = encryptionService.encrypt(plaintext, correctAad);

        assertThatThrownBy(() -> encryptionService.decrypt(payload, attackerSwappedAad))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SECRET_DECRYPTION_FAILED"));
    }

    @Test
    @DisplayName("11. Multiline, unicode, and binary payloads encrypt and decrypt cleanly")
    void testComplexPayloads() {
        String multilineJson = """
                {
                    "private_key": "-----BEGIN RSA PRIVATE KEY-----\\nMIIEowIBAAKCAQEA0\\n-----END RSA PRIVATE KEY-----",
                    "cert_chain": "🔐 🔒 🛡️ UTF-8 Symbols & Japanese 秘密の鍵",
                    "timeout_ms": 5000
                }
                """;

        EncryptedPayload payload = encryptionService.encrypt(
                multilineJson.getBytes(StandardCharsets.UTF_8),
                "secret-rsa:env-prod:3"
        );

        byte[] decrypted = encryptionService.decrypt(payload, "secret-rsa:env-prod:3");
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(multilineJson);
    }
}
