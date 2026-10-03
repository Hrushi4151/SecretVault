package com.secretvault.secret.reveal.service;

import com.secretvault.secret.dto.SecretRevealResponse;
import com.secretvault.secret.reveal.dto.CreateRevealIntentRequest;
import com.secretvault.secret.reveal.dto.ExecuteRevealRequest;
import com.secretvault.secret.reveal.dto.SecretRevealAuditResponse;
import com.secretvault.secret.reveal.dto.SecretRevealIntentResponse;
import com.secretvault.secret.reveal.model.SecretRevealPolicyEvaluation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SecretRevealService {

    String CATEGORY_SECRET_REVEAL_INTENT = "secret_reveal_intent";

    /**
     * Resolves the effective reveal policy for a secret.
     */
    SecretRevealPolicyEvaluation getRevealPolicy(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId
    );

    /**
     * Phase 1 of Two-Phase Reveal:
     * Validates permission, policy requirements, justification reason, and consumes Step-Up proof (if required).
     * Issues an ephemeral, cryptographically bound single-use reveal intent token.
     */
    SecretRevealIntentResponse createRevealIntent(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            CreateRevealIntentRequest request,
            UUID userId,
            String sessionIdentifier,
            String requestId,
            String ipAddress
    );

    /**
     * Phase 2 of Two-Phase Reveal (or Direct Single-Phase Atomic Reveal):
     * Consumes the single-use reveal intent token atomically from Redis,
     * re-evaluates authorization in real-time (Time-of-Use TOCTOU check),
     * decrypts in-memory, zeroizes intermediate buffers, logs audit, and returns plaintext payload.
     */
    SecretRevealResponse executeReveal(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            ExecuteRevealRequest request,
            UUID userId,
            String sessionIdentifier,
            String requestId,
            String ipAddress
    );

    /**
     * Historical version reveal routed through the authoritative pipeline.
     */
    SecretRevealResponse revealHistoricalVersion(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            ExecuteRevealRequest request,
            UUID userId,
            String sessionIdentifier,
            String requestId,
            String ipAddress
    );

    /**
     * Security Administrator visibility into secret reveal audit history.
     */
    Page<SecretRevealAuditResponse> getRevealAuditHistory(
            UUID workspaceId,
            UUID actorUserId,
            Pageable pageable
    );
}
