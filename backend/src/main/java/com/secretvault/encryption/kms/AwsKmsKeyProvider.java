package com.secretvault.encryption.kms;

import com.secretvault.common.exception.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.KmsClientBuilder;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/**
 * Production AWS KMS Key Provider for Key Encryption Key (KEK) management.
 * Uses AWS KMS Customer Managed Keys (CMKs) to wrap and unwrap 256-bit Data Encryption Keys (DEKs).
 * Supports IAM Task Roles, EKS Pod Identity/IRSA, and LocalStack integration.
 */
@Component
@ConditionalOnProperty(name = "secretvault.kms.provider", havingValue = "aws")
public class AwsKmsKeyProvider implements KmsKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(AwsKmsKeyProvider.class);
    private static final int EXPECTED_KEY_BYTES = 32; // 256-bit DEK

    private final String keyIdOrAlias;
    private final String regionName;
    private final String endpointOverride;
    private KmsClient kmsClient;
    private final boolean injectedClient;

    public AwsKmsKeyProvider(
            @Value("${secretvault.kms.aws.key-id:alias/secretvault-kek}") String keyIdOrAlias,
            @Value("${secretvault.kms.aws.region:us-east-1}") String regionName,
            @Value("${secretvault.kms.aws.endpoint:}") String endpointOverride
    ) {
        this.keyIdOrAlias = keyIdOrAlias;
        this.regionName = regionName;
        this.endpointOverride = endpointOverride;
        this.injectedClient = false;
    }

    /**
     * Package-private constructor for unit and integration testing with mocked KmsClient.
     */
    AwsKmsKeyProvider(KmsClient kmsClient, String defaultKeyReference) {
        this.kmsClient = Objects.requireNonNull(kmsClient, "kmsClient must not be null");
        this.keyIdOrAlias = StringUtils.hasText(defaultKeyReference) ? defaultKeyReference : "alias/secretvault-kek";
        this.regionName = "us-east-1";
        this.endpointOverride = "";
        this.injectedClient = true;
    }

    @PostConstruct
    public void init() {
        if (!injectedClient) {
            KmsClientBuilder builder = KmsClient.builder()
                    .region(Region.of(regionName));

            if (StringUtils.hasText(endpointOverride)) {
                builder.endpointOverride(URI.create(endpointOverride.trim()));
                log.info("Configured AWS KMS client with custom endpoint override [{}]", endpointOverride);
            }

            this.kmsClient = builder.build();
            log.info("Initialized AwsKmsKeyProvider with active KEK [{}] in region [{}]", keyIdOrAlias, regionName);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (kmsClient != null && !injectedClient) {
            try {
                kmsClient.close();
                log.info("Closed AWS KMS client connection cleanly");
            } catch (Exception e) {
                log.warn("Error closing AWS KMS client: {}", e.getMessage());
            }
        }
    }

    @Override
    public byte[] wrapKey(byte[] plaintextDek, String keyReference) {
        Objects.requireNonNull(plaintextDek, "Plaintext DEK must not be null");
        if (plaintextDek.length != EXPECTED_KEY_BYTES) {
            throw new IllegalArgumentException("Plaintext DEK must be 32 bytes (256-bit)");
        }

        String targetKey = StringUtils.hasText(keyReference) ? keyReference : this.keyIdOrAlias;

        try {
            SdkBytes plaintextBytes = SdkBytes.fromByteArray(plaintextDek);
            EncryptRequest request = EncryptRequest.builder()
                    .keyId(targetKey)
                    .plaintext(plaintextBytes)
                    .build();

            EncryptResponse response = kmsClient.encrypt(request);
            return response.ciphertextBlob().asByteArray();
        } catch (KmsException e) {
            log.error("AWS KMS wrapKey operation failed for keyReference [{}]: {}", targetKey, e.awsErrorDetails().errorMessage());
            throw ApiException.internal("KMS_WRAP_FAILED", "Key Management Service failed to protect Data Encryption Key", e);
        } catch (Exception e) {
            log.error("Unexpected error during AWS KMS wrapKey: {}", e.getMessage());
            throw ApiException.internal("KMS_UNAVAILABLE", "Key Management Service is temporarily unavailable", e);
        }
    }

    @Override
    public byte[] unwrapKey(byte[] encryptedDek, String keyReference) {
        Objects.requireNonNull(encryptedDek, "Encrypted DEK must not be null");

        String targetKey = StringUtils.hasText(keyReference) ? keyReference : this.keyIdOrAlias;

        byte[] unwrappedBytes = null;
        try {
            SdkBytes ciphertextBytes = SdkBytes.fromByteArray(encryptedDek);
            DecryptRequest.Builder builder = DecryptRequest.builder()
                    .ciphertextBlob(ciphertextBytes);

            if (StringUtils.hasText(targetKey)) {
                builder.keyId(targetKey);
            }

            DecryptResponse response = kmsClient.decrypt(builder.build());
            unwrappedBytes = response.plaintext().asByteArray();

            if (unwrappedBytes.length != EXPECTED_KEY_BYTES) {
                throw ApiException.badRequest("SECRET_DECRYPTION_FAILED", "Unwrapped DEK has invalid length");
            }

            // Return a safe copy; caller will zeroize when done
            return Arrays.copyOf(unwrappedBytes, unwrappedBytes.length);
        } catch (KmsException e) {
            log.warn("AWS KMS unwrapKey operation rejected: {}", e.awsErrorDetails().errorMessage());
            throw ApiException.badRequest("SECRET_DECRYPTION_FAILED", "Failed to unwrap Data Encryption Key via KMS");
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("Unexpected error during AWS KMS unwrapKey: {}", e.getMessage());
            throw ApiException.internal("KMS_UNAVAILABLE", "Key Management Service is temporarily unavailable", e);
        } finally {
            if (unwrappedBytes != null) {
                Arrays.fill(unwrappedBytes, (byte) 0);
            }
        }
    }

    @Override
    public String getDefaultKeyReference() {
        return keyIdOrAlias;
    }
}
