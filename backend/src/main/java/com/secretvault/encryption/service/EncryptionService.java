package com.secretvault.encryption.service;

import com.secretvault.encryption.model.EncryptedPayload;

/**
 * High-level cryptographic service interface for envelope encryption and decryption.
 */
public interface EncryptionService {

    /**
     * Encrypts plaintext bytes using a fresh 256-bit DEK, 96-bit random IV, and AES-256-GCM.
     * DEK is wrapped using the active KEK provider.
     *
     * @param plaintext Raw bytes to encrypt
     * @param aadContext Authenticated Additional Data context binding (e.g., secretId:envId:versionNumber)
     * @return EncryptedPayload with ciphertext, wrapped DEK, IV, auth tag, and key reference
     */
    EncryptedPayload encrypt(byte[] plaintext, String aadContext);

    /**
     * Decrypts an EncryptedPayload by unwrapping the DEK, verifying the authentication tag and AAD context,
     * and returning the raw plaintext bytes. Decryption is performed entirely in memory.
     *
     * @param payload EncryptedPayload holding ciphertext, wrapped DEK, IV, auth tag, and key reference
     * @param aadContext Authenticated Additional Data context binding
     * @return Decrypted plaintext bytes
     */
    byte[] decrypt(EncryptedPayload payload, String aadContext);
}
