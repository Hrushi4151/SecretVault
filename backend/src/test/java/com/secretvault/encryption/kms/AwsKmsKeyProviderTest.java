package com.secretvault.encryption.kms;

import com.secretvault.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsKmsKeyProviderTest {

    private KmsClient mockKmsClient;
    private AwsKmsKeyProvider provider;
    private final String defaultKeyAlias = "alias/secretvault-production-kek";

    @BeforeEach
    void setUp() {
        mockKmsClient = mock(KmsClient.class);
        provider = new AwsKmsKeyProvider(mockKmsClient, defaultKeyAlias);
    }

    @Test
    @DisplayName("wrapKey: successfully wraps 32-byte DEK via AWS KMS Encrypt API")
    void testWrapKeySuccess() {
        byte[] plaintextDek = new byte[32];
        new SecureRandom().nextBytes(plaintextDek);
        byte[] expectedCiphertext = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        EncryptResponse encryptResponse = EncryptResponse.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(expectedCiphertext))
                .keyId("arn:aws:kms:us-east-1:123456789012:key/test-uuid")
                .build();

        when(mockKmsClient.encrypt(any(EncryptRequest.class))).thenReturn(encryptResponse);

        byte[] wrapped = provider.wrapKey(plaintextDek, null);

        assertThat(wrapped).isEqualTo(expectedCiphertext);

        ArgumentCaptor<EncryptRequest> captor = ArgumentCaptor.forClass(EncryptRequest.class);
        verify(mockKmsClient).encrypt(captor.capture());
        assertThat(captor.getValue().keyId()).isEqualTo(defaultKeyAlias);
        assertThat(captor.getValue().plaintext().asByteArray()).isEqualTo(plaintextDek);
    }

    @Test
    @DisplayName("wrapKey: validates 32-byte DEK length and rejects malformed inputs")
    void testWrapKeyInvalidLength() {
        byte[] invalidDek = new byte[16];

        assertThatThrownBy(() -> provider.wrapKey(invalidDek, defaultKeyAlias))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("wrapKey: maps KmsException to ApiException internal error")
    void testWrapKeyKmsException() {
        byte[] plaintextDek = new byte[32];
        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .errorMessage("AccessDeniedException: User is not authorized to perform kms:Encrypt")
                .errorCode("AccessDeniedException")
                .build();
        KmsException kmsException = (KmsException) KmsException.builder()
                .awsErrorDetails(errorDetails)
                .message("Access Denied")
                .build();

        when(mockKmsClient.encrypt(any(EncryptRequest.class))).thenThrow(kmsException);

        assertThatThrownBy(() -> provider.wrapKey(plaintextDek, defaultKeyAlias))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException ae = (ApiException) ex;
                    assertThat(ae.getCode()).isEqualTo("KMS_WRAP_FAILED");
                });
    }

    @Test
    @DisplayName("unwrapKey: successfully unwraps encrypted DEK via AWS KMS Decrypt API")
    void testUnwrapKeySuccess() {
        byte[] encryptedDek = new byte[]{10, 20, 30, 40};
        byte[] expectedPlaintextDek = new byte[32];
        Arrays.fill(expectedPlaintextDek, (byte) 7);

        DecryptResponse decryptResponse = DecryptResponse.builder()
                .plaintext(SdkBytes.fromByteArray(expectedPlaintextDek))
                .build();

        when(mockKmsClient.decrypt(any(DecryptRequest.class))).thenReturn(decryptResponse);

        byte[] unwrapped = provider.unwrapKey(encryptedDek, defaultKeyAlias);

        assertThat(unwrapped).isEqualTo(expectedPlaintextDek);

        ArgumentCaptor<DecryptRequest> captor = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(mockKmsClient).decrypt(captor.capture());
        assertThat(captor.getValue().ciphertextBlob().asByteArray()).isEqualTo(encryptedDek);
        assertThat(captor.getValue().keyId()).isEqualTo(defaultKeyAlias);
    }

    @Test
    @DisplayName("unwrapKey: maps KmsException to badRequest SECRET_DECRYPTION_FAILED")
    void testUnwrapKeyKmsException() {
        byte[] encryptedDek = new byte[]{10, 20, 30, 40};
        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .errorMessage("InvalidCiphertextException: Key ID does not match ciphertext")
                .errorCode("InvalidCiphertextException")
                .build();
        KmsException kmsException = (KmsException) KmsException.builder()
                .awsErrorDetails(errorDetails)
                .message("Invalid Ciphertext")
                .build();

        when(mockKmsClient.decrypt(any(DecryptRequest.class))).thenThrow(kmsException);

        assertThatThrownBy(() -> provider.unwrapKey(encryptedDek, defaultKeyAlias))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException ae = (ApiException) ex;
                    assertThat(ae.getCode()).isEqualTo("SECRET_DECRYPTION_FAILED");
                });
    }

    @Test
    @DisplayName("getDefaultKeyReference: returns configured default key reference")
    void testGetDefaultKeyReference() {
        assertThat(provider.getDefaultKeyReference()).isEqualTo(defaultKeyAlias);
    }
}
