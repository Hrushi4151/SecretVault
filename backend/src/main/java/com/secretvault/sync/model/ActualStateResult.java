package com.secretvault.sync.model;

import java.util.Collections;
import java.util.List;

/**
 * Result container for provider actual state resolution.
 * Distinguishes between successful observations and provider-side errors
 * (e.g. PROVIDER_UNAVAILABLE vs PERMISSION_DENIED vs UNSUPPORTED).
 */
public record ActualStateResult(
        boolean success,
        List<ProviderSecretState> states,
        DriftType errorDriftType,
        String errorCode,
        String errorMessage
) {
    public static ActualStateResult success(List<ProviderSecretState> states) {
        return new ActualStateResult(
                true,
                states != null ? states : Collections.emptyList(),
                null,
                null,
                null
        );
    }

    public static ActualStateResult error(DriftType errorDriftType, String errorCode, String errorMessage) {
        return new ActualStateResult(
                false,
                Collections.emptyList(),
                errorDriftType != null ? errorDriftType : DriftType.PROVIDER_UNAVAILABLE,
                errorCode,
                errorMessage
        );
    }

    public boolean isError() {
        return !success;
    }
}
