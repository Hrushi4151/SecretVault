package com.secretvault.encryption.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.kms.KmsKeyProvider;
import com.secretvault.encryption.model.EncryptedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Production-grade AES-256-GCM envelope encryption service.
 * Generates fresh 256-bit DEKs and 96-bit random IVs for every encryption operation.
 * Binds Authenticated Additional Data (AAD) to prevent ciphertext cross-tenant swapping.
 */
@Service
public class AesGcmEnvelopeEncryptionService implements EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(AesGcmEnvelopeEncryptionService.class);

    private static final String GCM_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int DEK_SIZE_BYTES = 32;       // 256 bits
    private static final int GCM_IV_SIZE_BYTES = 12;     // 96 bits
    private static final int GCM_TAG_LENGTH_BITS = 128; // 128 bits
    private static final int GCM_TAG_LENGTH_BYTES = 16;  // 16 bytes

    private final KmsKeyProvider kmsKeyProvider;
    private final SecureRandom secureRandom;

    public AesGcmEnvelopeEncryptionService(KmsKeyProvider kmsKeyProvider) {
        this.kmsKeyProvider = kmsKeyProvider;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public EncryptedPayload encrypt(byte[] plaintext, String aadContext) {
        Objects.requireNonNull(plaintext, "Plaintext to encrypt must not be null");

        // 1. Generate fresh 256-bit DEK
        byte[] dekBytes = new byte[DEK_SIZE_BYTES];
        secureRandom.nextBytes(dekBytes);

        // 2. Generate fresh 96-bit IV
        byte[] iv = new byte[GCM_IV_SIZE_BYTES];
        secureRandom.nextBytes(iv);

        try {
            SecretKey dekKey = new SecretKeySpec(dekBytes, "AES");
            Cipher cipher = Cipher.getInstance(GCM_CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, dekKey, gcmSpec);

            if (StringUtils.hasText(aadContext)) {
                cipher.updateAAD(aadContext.getBytes(StandardCharsets.UTF_8));
            }

            byte[] combined = cipher.doFinal(plaintext);
            int ciphertextLen = combined.length - GCM_TAG_LENGTH_BYTES;
            byte[] ciphertext = Arrays.copyOfRange(combined, 0, ciphertextLen);
            byte[] authTag = Arrays.copyOfRange(combined, ciphertextLen, combined.length);

            // 3. Wrap DEK with KEK
            String keyRef = kmsKeyProvider.getDefaultKeyReference();
            byte[] encryptedDek = kmsKeyProvider.wrapKey(dekBytes, keyRef);

            return new EncryptedPayload(ciphertext, encryptedDek, iv, authTag, keyRef);
        } catch (GeneralSecurityException e) {
            log.error("Envelope encryption operation failed unexpectedly");
            throw ApiException.internal("ENCRYPTION_FAILED", "Failed to encrypt secret payload", e);
        } finally {
            // Memory zeroization of temporary plaintext DEK
            Arrays.fill(dekBytes, (byte) 0);
        }
    }

    @Override
    public byte[] decrypt(EncryptedPayload payload, String aadContext) {
        Objects.requireNonNull(payload, "Encrypted payload must not be null");
        Objects.requireNonNull(payload.ciphertext(), "Ciphertext must not be null");
        Objects.requireNonNull(payload.encryptedDek(), "Encrypted DEK must not be null");
        Objects.requireNonNull(payload.iv(), "IV must not be null");
        Objects.requireNonNull(payload.authTag(), "Auth tag must not be null");

        if (payload.iv().length != GCM_IV_SIZE_BYTES) {
            throw ApiException.badRequest("SECRET_DECRYPTION_FAILED", "Invalid IV length");
        }
        if (payload.authTag().length != GCM_TAG_LENGTH_BYTES) {
            throw ApiException.badRequest("SECRET_DECRYPTION_FAILED", "Invalid authentication tag length");
        }

        // 1. Unwrap DEK with KEK
        byte[] dekBytes = kmsKeyProvider.unwrapKey(payload.encryptedDek(), payload.keyReference());

        try {
            SecretKey dekKey = new SecretKeySpec(dekBytes, "AES");
            Cipher cipher = Cipher.getInstance(GCM_CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv());
            cipher.init(Cipher.DECRYPT_MODE, dekKey, gcmSpec);

            if (StringUtils.hasText(aadContext)) {
                cipher.updateAAD(aadContext.getBytes(StandardCharsets.UTF_8));
            }

            // Recombine ciphertext and authTag for standard JCE GCM cipher
            byte[] combined = new byte[payload.ciphertext().length + payload.authTag().length];
            System.arraycopy(payload.ciphertext(), 0, combined, 0, payload.ciphertext().length);
            System.arraycopy(payload.authTag(), 0, combined, payload.ciphertext().length, payload.authTag().length);

            return cipher.doFinal(combined);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Decryption failed: signature/tag mismatch or tampered payload");
            throw ApiException.badRequest("SECRET_DECRYPTION_FAILED", "Decryption failed or ciphertext has been tampered with");
        } finally {
            // Memory zeroization of temporary plaintext DEK
            Arrays.fill(dekBytes, (byte) 0);
        }
    }
}
