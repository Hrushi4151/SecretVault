package com.secretvault.secret.dto;

import com.secretvault.secret.entity.SecretVersionTag;

import java.time.Instant;
import java.util.UUID;

public record SecretVersionTagResponse(
        UUID id,
        UUID secretVersionId,
        String name,
        UUID createdBy,
        Instant createdAt
) {
    public static SecretVersionTagResponse fromEntity(SecretVersionTag tag) {
        return new SecretVersionTagResponse(
                tag.getId(),
                tag.getSecretVersionId(),
                tag.getName(),
                tag.getCreatedBy(),
                tag.getCreatedAt()
        );
    }
}
