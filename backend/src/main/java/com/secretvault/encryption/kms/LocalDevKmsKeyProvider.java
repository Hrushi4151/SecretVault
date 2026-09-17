package com.secretvault.encryption.kms;

import com.secretvault.common.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
public class LocalDevKmsKeyProvider implements KmsKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalDevKmsKeyProvider.class);
    private static final String DEFAULT_KEY_REF = "local-dev-kek-v1";
    private static final int EXPECTED_KEY_BYTES = 32; // 256 bits

    private final String masterKeyConfig;
    private SecretKeySpec kekSpec;

    public LocalDevKmsKeyProvider(@Value("${secretvault.security.master-key:}") String masterKeyConfig) {
        this.masterKeyConfig = masterKeyConfig;
    }

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(masterKeyConfig)) {
            throw new IllegalStateException("CRITICAL: Master Key Encryption Key (VAULT_MASTER_KEY) is not configured");
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(masterKeyConfig.trim());
            if (keyBytes.length != EXPECTED_KEY_BYTES) {
                throw new IllegalStateException(
                        "CRITICAL: Master KEK must be exactly 256 bits (32 bytes). Found: " + keyBytes.length + " bytes"
                );
            }
            this.kekSpec = new SecretKeySpec(keyBytes, "AES");
            log.info("Initialized LocalDevKmsKeyProvider with active KEK reference [{}] (256-bit AES)", DEFAULT_KEY_REF);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CRITICAL: Master KEK is not a valid Base64 string", e);
        }
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

        try {
            Cipher cipher = Cipher.getInstance("AESWrap");
            cipher.init(Cipher.UNWRAP_MODE, kekSpec);
            Key unwrappedKey = cipher.unwrap(encryptedDek, "AES", Cipher.SECRET_KEY);
            if (unwrappedKey == null || unwrappedKey.getEncoded() == null) {
                throw ApiException.badRequest("SECRET_DECRYPTION_FAILED", "Invalid or corrupted encrypted DEK");
            }
            return unwrappedKey.getEncoded();
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw ApiException.badRequest("SECRET_DECRYPTION_FAILED", "Failed to unwrap Data Encryption Key. KEK or DEK may be invalid");
        }
    }

    @Override
    public String getDefaultKeyReference() {
        return DEFAULT_KEY_REF;
    }
}
