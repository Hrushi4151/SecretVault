package com.secretvault.secret.reveal.model;

import java.util.List;

/**
 * Resolved, evaluated reveal policy requirements for a specific secret reveal target.
 */
public record SecretRevealPolicyEvaluation(
        RevealPolicyLevel policyLevel,
        boolean requireStepUp,
        List<String> allowedStepUpFactors,
        boolean requireWebAuthnOnly,
        boolean requireReason,
        int minReasonLength,
        int maxReasonLength,
        boolean requirePrivilegedOrJit,
        int maxDisplayDurationSeconds,
        boolean copyAllowed,
        int clipboardTimeoutSeconds,
        boolean bulkRevealAllowed,
        int maxBulkCount,
        int rateLimitPerMinute
) {

    public static SecretRevealPolicyEvaluation defaultPolicy() {
        return new SecretRevealPolicyEvaluation(
                RevealPolicyLevel.DEFAULT,
                false,
                List.of("PASSWORD", "TOTP", "RECOVERY_CODE", "WEBAUTHN"),
                false,
                false,
                10,
                500,
                false,
                60,
                true,
                15,
                false,
                50,
                60
        );
    }

    public static SecretRevealPolicyEvaluation forProduction() {
        return new SecretRevealPolicyEvaluation(
                RevealPolicyLevel.PRODUCTION_CRITICAL,
                true,
                List.of("PASSWORD", "TOTP", "RECOVERY_CODE", "WEBAUTHN"),
                false,
                true,
                10,
                500,
                false,
                30,
                true,
                15,
                false,
                10,
                15
        );
    }

    public static SecretRevealPolicyEvaluation forSensitive() {
        return new SecretRevealPolicyEvaluation(
                RevealPolicyLevel.SENSITIVE,
                false,
                List.of("PASSWORD", "TOTP", "RECOVERY_CODE", "WEBAUTHN"),
                false,
                false,
                10,
                500,
                false,
                60,
                true,
                15,
                false,
                25,
                30
        );
    }

    public static SecretRevealPolicyEvaluation forHighlySensitive() {
        return new SecretRevealPolicyEvaluation(
                RevealPolicyLevel.HIGHLY_SENSITIVE,
                true,
                List.of("PASSWORD", "TOTP", "RECOVERY_CODE", "WEBAUTHN"),
                false,
                true,
                10,
                500,
                false,
                45,
                true,
                15,
                false,
                15,
                20
        );
    }
}
