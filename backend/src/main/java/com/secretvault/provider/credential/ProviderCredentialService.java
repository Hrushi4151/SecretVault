package com.secretvault.provider.credential;

import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.model.ProviderType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Enterprise credential protection service for external platform providers.
 * Enforces AES-256-GCM envelope encryption with Authenticated Additional Data (AAD) context binding.
 */
@Service
public class ProviderCredentialService {

    private final EncryptionService encryptionService;

    public ProviderCredentialService(EncryptionService encryptionService) {
        this.encryptionService = Objects.requireNonNull(encryptionService, "EncryptionService must not be null");
    }

    /**
     * Envelope-encrypts a raw provider credential string and populates the entity's encrypted fields.
     * Binds AAD context strictly to (workspaceId : providerType : integrationId).
     */
    public void encryptAndSetCredentials(
            ProviderIntegration integration,
            String rawCredential
    ) {
        Objects.requireNonNull(integration, "ProviderIntegration must not be null");
        Objects.requireNonNull(rawCredential, "Raw credential must not be null");

        byte[] plaintextBytes = rawCredential.trim().getBytes(StandardCharsets.UTF_8);
        try {
            String aad = buildAadContext(integration.getWorkspaceId(), integration.getProviderType(), integration.getId());
            EncryptedPayload payload = encryptionService.encrypt(plaintextBytes, aad);

            integration.setEncryptedCredentialToken(Base64.getEncoder().encodeToString(payload.ciphertext()));
            integration.setEncryptedDek(Base64.getEncoder().encodeToString(payload.encryptedDek()));
            integration.setIv(Base64.getEncoder().encodeToString(payload.iv()));
            integration.setAuthTag(Base64.getEncoder().encodeToString(payload.authTag()));
            integration.setKeyReference(payload.keyReference());
            integration.setRedactedCredentialHint(generateRedactedHint(rawCredential, integration.getProviderType()));
        } finally {
            Arrays.fill(plaintextBytes, (byte) 0);
        }
    }

    /**
     * Decrypts an integration's stored credentials in memory for immediate external API usage.
     * Verified against the integration's cryptographic AAD context.
     */
    public String decryptCredential(ProviderIntegration integration) {
        Objects.requireNonNull(integration, "ProviderIntegration must not be null");

        byte[] ciphertext = Base64.getDecoder().decode(integration.getEncryptedCredentialToken());
        byte[] encryptedDek = Base64.getDecoder().decode(integration.getEncryptedDek());
        byte[] iv = Base64.getDecoder().decode(integration.getIv());
        byte[] authTag = Base64.getDecoder().decode(integration.getAuthTag());
        String keyRef = integration.getKeyReference();

        EncryptedPayload payload = new EncryptedPayload(ciphertext, encryptedDek, iv, authTag, keyRef);
        String aad = buildAadContext(integration.getWorkspaceId(), integration.getProviderType(), integration.getId());

        byte[] decryptedBytes = encryptionService.decrypt(payload, aad);
        try {
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(decryptedBytes, (byte) 0);
        }
    }

    /**
     * Generates a safe, non-revealing hint suitable for UI display and audit logging.
     * Example: "vcel_...4a8f" or "rnd_...b39c" or "***1234".
     */
    public String generateRedactedHint(String rawCredential, ProviderType providerType) {
        if (rawCredential == null || rawCredential.isBlank()) {
            return "******";
        }
        String clean = rawCredential.trim();
        if (clean.length() <= 8) {
            return "***" + clean.substring(Math.max(0, clean.length() - 2));
        }

        String prefix = "";
        if (clean.startsWith("vcel_") || clean.startsWith("rnd_") || clean.startsWith("ghp_") || clean.startsWith("pat_")) {
            int underscoreIdx = clean.indexOf('_');
            prefix = clean.substring(0, underscoreIdx + 1);
        } else if (clean.length() >= 12) {
            prefix = clean.substring(0, 3);
        }

        String suffix = clean.substring(clean.length() - 4);
        return prefix + "..." + suffix;
    }

    private String buildAadContext(UUID workspaceId, ProviderType providerType, UUID integrationId) {
        return workspaceId.toString() + ":" + providerType.name() + ":" + integrationId.toString();
    }
}
