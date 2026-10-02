package com.secretvault.provider.dto;

import com.secretvault.provider.model.ProviderCapability;
import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.model.ProviderValidationResult;

import java.time.Instant;
import java.util.Set;

public record ValidateIntegrationResponse(
        boolean valid,
        String accountOrTeamName,
        String accountId,
        Set<ProviderCapability> capabilities,
        ProviderErrorCode errorCode,
        String errorMessage,
        Instant validatedAt
) {
    public static ValidateIntegrationResponse fromResult(ProviderValidationResult result) {
        return new ValidateIntegrationResponse(
                result.valid(),
                result.accountOrTeamName(),
                result.accountId(),
                result.capabilities(),
                result.errorCode(),
                result.errorMessage(),
                Instant.now()
        );
    }
}
