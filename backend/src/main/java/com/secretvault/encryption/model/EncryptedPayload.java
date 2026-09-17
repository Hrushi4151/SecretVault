package com.secretvault.encryption.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable value object representing an envelope-encrypted payload.
 * Stores AES-256-GCM ciphertext, wrapped DEK, IV, authentication tag, and KEK reference.
 */
public record EncryptedPayload(
        byte[] ciphertext,
        byte[] encryptedDek,
        byte[] iv,
        byte[] authTag,
        String keyReference
) {
    public EncryptedPayload {
        Objects.requireNonNull(ciphertext, "Ciphertext must not be null");
        Objects.requireNonNull(encryptedDek, "Encrypted DEK must not be null");
        Objects.requireNonNull(iv, "IV must not be null");
        Objects.requireNonNull(authTag, "Authentication tag must not be null");
        Objects.requireNonNull(keyReference, "Key reference must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EncryptedPayload that)) return false;
        return Arrays.equals(ciphertext, that.ciphertext) &&
                Arrays.equals(encryptedDek, that.encryptedDek) &&
                Arrays.equals(iv, that.iv) &&
                Arrays.equals(authTag, that.authTag) &&
                Objects.equals(keyReference, that.keyReference);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keyReference);
        result = 31 * result + Arrays.hashCode(ciphertext);
        result = 31 * result + Arrays.hashCode(encryptedDek);
        result = 31 * result + Arrays.hashCode(iv);
        result = 31 * result + Arrays.hashCode(authTag);
        return result;
    }

    @Override
    public String toString() {
        return "EncryptedPayload[keyReference=" + keyReference +
                ", ciphertextLen=" + ciphertext.length +
                ", ivLen=" + iv.length +
                ", tagLen=" + authTag.length + "]";
    }
}
