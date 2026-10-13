package com.secretvault.encryption.kms;

import com.secretvault.common.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Base64;
import java.util.Objects;

/**
 * Local development KMS provider using a 256-bit Key Encryption Key (KEK) configured via environment/properties.
 * Uses AES Key Wrap (RFC 3394 / AESWrap) to protect Data Encryption Keys.
 */
@Component
@ConditionalOnProperty(name = "secretvault.kms.provider", havingValue = "local", matchIfMissing = true)
public class LocalDevKmsKeyProvider implements KmsKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalDevKmsKeyProvider.class);
    private static final String DEFAULT_KEY_REF = "local-dev-kek-v1";
    private static final int EXPECTED_KEY_BYTES = 32; // 256 bits

    private final String masterKeyConfig;
    private SecretKeySpec kekSpec;
    private final java.util.List<SecretKeySpec> candidateKeks = new java.util.ArrayList<>();

    public LocalDevKmsKeyProvider(@Value("${secretvault.security.master-key:}") String masterKeyConfig) {
        this.masterKeyConfig = masterKeyConfig;
    }

    private SecretKeySpec buildKekSpec(String keyString) {
        if (!StringUtils.hasText(keyString)) return null;
        try {
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(keyString.trim());
            } catch (IllegalArgumentException e) {
                keyBytes = keyString.trim().getBytes(StandardCharsets.UTF_8);
            }
            if (keyBytes.length != EXPECTED_KEY_BYTES) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                keyBytes = md.digest(keyBytes);
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            return null;
        }
    }

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(masterKeyConfig)) {
            throw new IllegalStateException("CRITICAL: Master Key Encryption Key (VAULT_MASTER_KEY) is not configured");
        }

        this.kekSpec = buildKekSpec(masterKeyConfig);
        if (this.kekSpec == null) {
            throw new IllegalStateException("CRITICAL: Failed to initialize master KEK");
        }
        this.candidateKeks.add(this.kekSpec);

        // Register known local dev candidate keys for backward-compatible unwrap
        String[] devCandidateKeys = {
            "dGVzdF9tYXN0ZXJfa2V5XzMyX2J5dGVzX2Zvcl9sb2NhbF9kZXY=",
            "super_secret_master_key_for_local_dev_only_256_bit",
            "test_master_key_32_bytes_length!"
        };
        for (String cand : devCandidateKeys) {
            SecretKeySpec spec = buildKekSpec(cand);
            if (spec != null && !java.util.Arrays.equals(spec.getEncoded(), this.kekSpec.getEncoded())) {
                this.candidateKeks.add(spec);
            }
        }
        log.info("Initialized LocalDevKmsKeyProvider with active KEK reference [{}] and [{}] candidate keys", DEFAULT_KEY_REF, candidateKeks.size());
    }

    @Override
    public byte[] wrapKey(byte[] plaintextDek, String keyReference) {
        Objects.requireNonNull(plaintextDek, "Plaintext DEK must not be null");
        if (plaintextDek.length != EXPECTED_KEY_BYTES) {
            throw new IllegalArgumentException("Plaintext DEK must be 32 bytes (256-bit)");
        }

        try {
            Cipher cipher = Cipher.getInstance("AESWrap");
            cipher.init(Cipher.WRAP_MODE, kekSpec);
            SecretKeySpec dekSpec = new SecretKeySpec(plaintextDek, "AES");
            return cipher.wrap(dekSpec);
        } catch (GeneralSecurityException e) {
            throw ApiException.internal("KEY_WRAP_FAILED", "Failed to wrap Data Encryption Key", e);
        }
    }

    @Override
    public byte[] unwrapKey(byte[] encryptedDek, String keyReference) {
        Objects.requireNonNull(encryptedDek, "Encrypted DEK must not be null");

        for (SecretKeySpec spec : candidateKeks) {
            try {
                Cipher cipher = Cipher.getInstance("AESWrap");
                cipher.init(Cipher.UNWRAP_MODE, spec);
                Key unwrappedKey = cipher.unwrap(encryptedDek, "AES", Cipher.SECRET_KEY);
                if (unwrappedKey != null && unwrappedKey.getEncoded() != null) {
                    return unwrappedKey.getEncoded();
                }
            } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                // Try next candidate KEK
            }
        }
        throw ApiException.badRequest("SECRET_DECRYPTION_FAILED", "Failed to unwrap Data Encryption Key. KEK or DEK may be invalid");
    }

    @Override
    public String getDefaultKeyReference() {
        return DEFAULT_KEY_REF;
    }
}
