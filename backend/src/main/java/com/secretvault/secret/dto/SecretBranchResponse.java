package com.secretvault.secret.dto;

import com.secretvault.secret.entity.BranchStatus;
import com.secretvault.secret.entity.SecretBranch;

import java.time.Instant;
import java.util.UUID;

public record SecretBranchResponse(
        UUID id,
        UUID secretId,
        String name,
        String description,
        UUID baseVersionId,
        Integer baseVersionNumber,
        UUID headVersionId,
        Integer headVersionNumber,
        BranchStatus status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant mergedAt,
        UUID mergedBy
) {
    public static SecretBranchResponse fromEntity(SecretBranch branch, Integer baseVersionNumber, Integer headVersionNumber) {
        return new SecretBranchResponse(
                branch.getId(),
                branch.getSecretId(),
                branch.getName(),
                branch.getDescription(),
                branch.getBaseVersionId(),
                baseVersionNumber,
                branch.getHeadVersionId(),
                headVersionNumber,
                branch.getStatus(),
                branch.getCreatedBy(),
                branch.getCreatedAt(),
                branch.getUpdatedAt(),
                branch.getMergedAt(),
                branch.getMergedBy()
        );
    }
}
