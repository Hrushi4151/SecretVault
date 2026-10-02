package com.secretvault.provider.credential;

import com.secretvault.encryption.kms.LocalDevKmsKeyProvider;
import com.secretvault.encryption.service.AesGcmEnvelopeEncryptionService;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderCredentialServiceTest {

    private ProviderCredentialService credentialService;
    private UUID workspaceId;
    private UUID userId;

    private static final String TEST_KEK_BASE64 = java.util.Base64.getEncoder().encodeToString(
            "test_master_key_32_bytes_dev_001".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );

    @BeforeEach
    void setUp() {
        LocalDevKmsKeyProvider kmsKeyProvider = new LocalDevKmsKeyProvider(TEST_KEK_BASE64);
        kmsKeyProvider.init();
        EncryptionService encryptionService = new AesGcmEnvelopeEncryptionService(kmsKeyProvider);
        credentialService = new ProviderCredentialService(encryptionService);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Encrypt and decrypt provider credentials round-trip with zero plaintext leakage")
    void testEncryptAndDecryptRoundTrip() {
        ProviderIntegration integration = new ProviderIntegration();
        integration.setId(UUID.randomUUID());
        integration.setWorkspaceId(workspaceId);
        integration.setProviderType(ProviderType.VERCEL);
        integration.setDisplayName("Vercel Production");
        integration.setStatus(IntegrationStatus.VALIDATING);
        integration.setCreatedBy(userId);

        String secretToken = "vcel_super_secret_token_1234567890abcdef";
        credentialService.encryptAndSetCredentials(integration, secretToken);

        // Assert encrypted fields are populated
        assertThat(integration.getEncryptedCredentialToken()).isNotBlank().isNotEqualTo(secretToken);
        assertThat(integration.getEncryptedDek()).isNotBlank();
        assertThat(integration.getIv()).isNotBlank();
        assertThat(integration.getAuthTag()).isNotBlank();
        assertThat(integration.getKeyReference()).isNotBlank();
        assertThat(integration.getRedactedCredentialHint()).isEqualTo("vcel_...cdef");

        // Decrypt and assert match
        String decrypted = credentialService.decryptCredential(integration);
        assertThat(decrypted).isEqualTo(secretToken);
    }

    @Test
    @DisplayName("AAD Context binding prevents decryption if workspace or provider type is altered")
    void testAadContextTamperRejection() {
        ProviderIntegration integration = new ProviderIntegration();
        integration.setId(UUID.randomUUID());
        integration.setWorkspaceId(workspaceId);
        integration.setProviderType(ProviderType.RENDER);
        integration.setDisplayName("Render Staging");
        integration.setStatus(IntegrationStatus.ACTIVE);
        integration.setCreatedBy(userId);

        String secretToken = "rnd_token_9876543210zyxwvutsrq";
        credentialService.encryptAndSetCredentials(integration, secretToken);

        // Tamper with workspace ID
        integration.setWorkspaceId(UUID.randomUUID());

        assertThatThrownBy(() -> credentialService.decryptCredential(integration))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Redacted hint generation formats various token prefixes safely")
    void testRedactedHintFormatting() {
        assertThat(credentialService.generateRedactedHint("vcel_abcdef1234567890", ProviderType.VERCEL))
                .isEqualTo("vcel_...7890");
        assertThat(credentialService.generateRedactedHint("rnd_1234567890abcdef", ProviderType.RENDER))
                .isEqualTo("rnd_...cdef");
        assertThat(credentialService.generateRedactedHint("plain_generic_token_1234", ProviderType.AWS))
                .isEqualTo("pla...1234");
        assertThat(credentialService.generateRedactedHint("short", ProviderType.VERCEL))
                .isEqualTo("***rt");
        assertThat(credentialService.generateRedactedHint("", ProviderType.VERCEL))
                .isEqualTo("******");
    }
}
