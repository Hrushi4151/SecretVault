package com.secretvault.encryption.kms;

/**
 * Key Management Service (KMS) provider abstraction for Key Encryption Key (KEK) operations.
 * Wraps and unwraps Data Encryption Keys (DEKs) for envelope encryption.
 * Designed to support LocalDev, AWS KMS, Google Cloud KMS, and Hardware Security Modules (HSMs).
 */
public interface KmsKeyProvider {

    /**
     * Wraps a 256-bit plaintext Data Encryption Key using the specified KEK reference.
     *
     * @param plaintextDek 32-byte raw DEK
     * @param keyReference Identifier/version of the KEK
     * @return Encrypted (wrapped) DEK bytes
     */
    byte[] wrapKey(byte[] plaintextDek, String keyReference);

    /**
     * Unwraps an encrypted Data Encryption Key using the specified KEK reference.
     *
     * @param encryptedDek Wrapped DEK bytes
     * @param keyReference Identifier/version of the KEK
     * @return 32-byte raw plaintext DEK
     */
    byte[] unwrapKey(byte[] encryptedDek, String keyReference);

    /**
     * Returns the default active KEK reference identifier for new encryptions.
     */
    String getDefaultKeyReference();
}
