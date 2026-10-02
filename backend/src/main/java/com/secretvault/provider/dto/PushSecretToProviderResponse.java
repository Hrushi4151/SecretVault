package com.secretvault.provider.dto;

import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.model.ProviderType;

import java.time.Instant;
import java.util.UUID;

public record PushSecretToProviderResponse(
        boolean success,
        UUID secretId,
        String secretKey,
        ProviderType providerType,
        String providerResourceId,
        String providerEnvironment,
        String providerSecretId,
        String operation,
        Instant pushedAt,
        ProviderErrorCode errorCode,
        String errorMessage
) {
    public static PushSecretToProviderResponse success(
            UUID secretId,
            String secretKey,
            ProviderType providerType,
            String providerResourceId,
            String providerEnvironment,
            String providerSecretId,
            String operation
    ) {
        return new PushSecretToProviderResponse(
                true,
                secretId,
                secretKey,
                providerType,
                providerResourceId,
                providerEnvironment,
                providerSecretId,
                operation,
                Instant.now(),
                null,
                null
        );
    }

    public static PushSecretToProviderResponse failure(
            UUID secretId,
            String secretKey,
            ProviderType providerType,
            String providerResourceId,
            String providerEnvironment,
            ProviderErrorCode errorCode,
            String errorMessage
    ) {
        return new PushSecretToProviderResponse(
                false,
                secretId,
                secretKey,
                providerType,
                providerResourceId,
                providerEnvironment,
                null,
                "PUSH",
                Instant.now(),
                errorCode,
                errorMessage
        );
    }
}
