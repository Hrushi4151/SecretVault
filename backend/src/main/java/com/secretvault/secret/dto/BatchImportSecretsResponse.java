package com.secretvault.secret.dto;

import java.util.List;

/**
 * Summary response for batch secret import operations.
 */
public record BatchImportSecretsResponse(
        int totalProcessed,
        int importedCount,
        int updatedCount,
        int skippedCount,
        List<SecretMetadataResponse> secrets
) {
}
