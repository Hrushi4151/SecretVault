package com.secretvault.secret.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for bulk importing secrets from a .env file or list.
 */
public record BatchImportSecretsRequest(
        @NotEmpty(message = "Secrets list cannot be empty")
        @Size(max = 200, message = "Cannot import more than 200 secrets in a single batch")
        List<@Valid CreateSecretRequest> secrets,

        boolean overwriteExisting
) {
}
