package com.secretvault.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validates AI model outputs for safety, hallucinations, and policy compliance.
 */
@Component
public class AiSafetyGuardrailValidator {

    private static final Logger log = LoggerFactory.getLogger(AiSafetyGuardrailValidator.class);

    private static final Pattern DESTRUCTIVE_COMMAND_PATTERN = Pattern.compile(
            "(?i)\\b(rm\\s+-rf\\s+/|chmod\\s+777|DROP\\s+DATABASE|DROP\\s+TABLE|mkfs|dd\\s+if=)\\b"
    );

    private final AiContextSanitizer sanitizer;

    public AiSafetyGuardrailValidator() {
        this(new AiContextSanitizer());
    }

    public AiSafetyGuardrailValidator(AiContextSanitizer sanitizer) {
        this.sanitizer = sanitizer != null ? sanitizer : new AiContextSanitizer();
    }

    public String validateAndSanitizeResponse(String modelResponse) {
        if (modelResponse == null || modelResponse.isBlank()) {
            return "No analytical insights generated for provided telemetry.";
        }

        // 1. Guard against destructive command suggestions
        if (DESTRUCTIVE_COMMAND_PATTERN.matcher(modelResponse).find()) {
            log.warn("Safety guardrail triggered: destructive system command detected in model output");
            return "Safety Notice: AI response contained unauthorized destructive instructions and was safely suppressed by the SecretVault Safety Engine.";
        }

        // 2. Enforce zero plaintext on model output
        return sanitizer.sanitizeText(modelResponse);
    }
}
