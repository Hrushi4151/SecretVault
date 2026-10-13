package com.secretvault.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.events.entity.DomainEventEntity;
import com.secretvault.events.entity.OutboxEvent;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.OutboxEventPublisher;
import com.secretvault.events.repository.DomainEventRepository;
import com.secretvault.events.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 13: Zero-Plaintext Secret Redaction & Canary Tests")
class EventRedactionSecurityCanaryTest {

    private static final String CANARY_SECRET_VALUE = "sec_canary_live_99f81a7b4430e8c02b";
    private static final String CANARY_API_KEY = "ghp_canaryGitHubToken999999999999";
    private static final String CANARY_PASSWORD = "SuperP@ssw0rd!Canary123";
    private static final String CANARY_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASC...CANARY\n-----END PRIVATE KEY-----";
    private static final String CANARY_BEARER = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.canary.token";
    private static final String CANARY_DEK = "raw-dek-canary-bytes-001122334455";

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    private ObjectMapper objectMapper;
    private OutboxEventPublisher publisher;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        workspaceId = UUID.randomUUID();
        publisher = new OutboxEventPublisher(domainEventRepository, outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("Security Canary: Plaintext canary secrets never appear in BaseDomainEvent metadata")
    void testCanariesRedactedInDomainEventMetadata() {
        Map<String, Object> dangerousMetadata = new HashMap<>();
        dangerousMetadata.put("secret", CANARY_SECRET_VALUE);
        dangerousMetadata.put("apiKey", CANARY_API_KEY);
        dangerousMetadata.put("password", CANARY_PASSWORD);
        dangerousMetadata.put("private_key", CANARY_PRIVATE_KEY);
        dangerousMetadata.put("authorization", CANARY_BEARER);
        dangerousMetadata.put("dek", CANARY_DEK);
        dangerousMetadata.put("safe_metric", 42);
        dangerousMetadata.put("safe_resource_type", "DATABASE");

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_UPDATED)
                .workspaceId(workspaceId)
                .severity(EventSeverity.INFO)
                .metadata(dangerousMetadata)
                .build();

        // 1. Assert memory representation in BaseDomainEvent
        for (String canary : new String[]{CANARY_SECRET_VALUE, CANARY_API_KEY, CANARY_PASSWORD, CANARY_PRIVATE_KEY, CANARY_BEARER, CANARY_DEK}) {
            assertThat(event.getMetadata().values()).doesNotContain(canary);
        }

        assertThat(event.getMetadata().get("secret")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("apiKey")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("password")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("private_key")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("authorization")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("dek")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("safe_metric")).isEqualTo(42);
        assertThat(event.getMetadata().get("safe_resource_type")).isEqualTo("DATABASE");
    }

    @Test
    @DisplayName("Security Canary: Persisted DomainEventEntity and OutboxEvent contain ZERO canary occurrences")
    void testCanariesNeverPersistedToDatabaseRows() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("secretValue", CANARY_SECRET_VALUE);
        metadata.put("user_password", CANARY_PASSWORD);
        metadata.put("api_token", CANARY_API_KEY);

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_VERSION_CREATED)
                .workspaceId(workspaceId)
                .aggregateType("SECRET")
                .aggregateId(UUID.randomUUID().toString())
                .metadata(metadata)
                .build();

        publisher.publish(event);

        // Verify DomainEventEntity row
        ArgumentCaptor<DomainEventEntity> domainCaptor = ArgumentCaptor.forClass(DomainEventEntity.class);
        verify(domainEventRepository, times(1)).save(domainCaptor.capture());
        DomainEventEntity savedDomain = domainCaptor.getValue();

        String domainPayloadJson = savedDomain.getPayloadJson();
        String domainMetadataJson = savedDomain.getMetadataJson();

        // Verify OutboxEvent row
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(outboxCaptor.capture());
        OutboxEvent savedOutbox = outboxCaptor.getValue();

        String outboxPayload = savedOutbox.getPayload();

        // Comprehensive search for ALL canaries across all persisted database row columns
        for (String canary : new String[]{CANARY_SECRET_VALUE, CANARY_API_KEY, CANARY_PASSWORD}) {
            assertThat(domainPayloadJson)
                    .as("Canary token found in domain_events.payload_json: %s", canary)
                    .doesNotContain(canary);

            assertThat(domainMetadataJson)
                    .as("Canary token found in domain_events.metadata_json: %s", canary)
                    .doesNotContain(canary);

            assertThat(outboxPayload)
                    .as("Canary token found in event_outbox.payload: %s", canary)
                    .doesNotContain(canary);
        }
    }

    @Test
    @DisplayName("Security Canary: Nested structures (maps in lists) recursively redact canary secrets")
    void testNestedStructuresCanariesRedacted() {
        Map<String, Object> nestedDetails = new HashMap<>();
        nestedDetails.put("private_key", CANARY_PRIVATE_KEY);
        nestedDetails.put("authorization", CANARY_BEARER);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("validationdetails", java.util.List.of(
                nestedDetails,
                Map.of("message", "Error connecting with auth header: " + CANARY_BEARER + " and key " + CANARY_PRIVATE_KEY)
        ));

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.ROTATION_VALIDATED)
                .workspaceId(workspaceId)
                .metadata(metadata)
                .build();

        String serialized = "";
        try {
            serialized = objectMapper.writeValueAsString(event);
        } catch (Exception ignored) {}

        // Assert that NONE of the canaries appear in the event representation or serialization
        for (String canary : new String[]{CANARY_PRIVATE_KEY, CANARY_BEARER}) {
            assertThat(serialized)
                    .as("Serialized nested event must not contain canary %s", canary)
                    .doesNotContain(canary);
        }
    }

    @Test
    @DisplayName("Zero-Plaintext: Reject unrecognized secret-bearing fields without logging rejected content")
    void testRejectionOfUnrecognizedSecretBearingFieldWithoutLogging() {
        Map<String, Object> metadataWithUnrecognizedSecret = new HashMap<>();
        metadataWithUnrecognizedSecret.put("custom_external_secret_token", CANARY_SECRET_VALUE);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            BaseDomainEvent.builder()
                    .eventType(EventType.SECRET_CREATED)
                    .workspaceId(workspaceId)
                    .metadata(metadataWithUnrecognizedSecret)
                    .build();
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejected without exposing content")
        // Invariant: Exception message must NEVER include the secret key or value
        .hasMessageNotContaining(CANARY_SECRET_VALUE)
        .hasMessageNotContaining("custom_external_secret_token");
    }

    @Test
    @DisplayName("Zero-Plaintext: EventDispatcher sanitizes provider exceptions containing auth headers and private keys")
    void testProviderErrorAndExceptionMessageSanitization() {
        String rawDownstreamError = "Provider HTTP 401 Unauthorized: Authorization: Bearer " + CANARY_BEARER
                + " failed with key: " + CANARY_PRIVATE_KEY
                + " token=" + CANARY_API_KEY
                + " canary=" + CANARY_SECRET_VALUE;

        String sanitized = com.secretvault.events.dispatcher.EventDispatcher.sanitizeErrorMessage(rawDownstreamError);

        for (String canary : new String[]{CANARY_BEARER, CANARY_PRIVATE_KEY, CANARY_API_KEY, CANARY_SECRET_VALUE}) {
            assertThat(sanitized)
                    .as("Sanitized error message must not contain canary %s", canary)
                    .doesNotContain(canary);
        }

        assertThat(sanitized).contains("[REDACTED]");
    }

    @Test
    @DisplayName("P0 Redaction: Allowlisted field 'key' with ordinary metadata is preserved")
    void testAllowlistedKeyWithOrdinaryMetadataPreserved() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "cache.primary.user_profile");
        metadata.put("source", "redis-cluster-node-01");
        metadata.put("version", "v2.1.0");

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_VERSION_CREATED)
                .workspaceId(workspaceId)
                .metadata(metadata)
                .build();

        assertThat(event.getMetadata().get("key")).isEqualTo("cache.primary.user_profile");
        assertThat(event.getMetadata().get("source")).isEqualTo("redis-cluster-node-01");
        assertThat(event.getMetadata().get("version")).isEqualTo("v2.1.0");
    }

    @Test
    @DisplayName("P0 Redaction: Allowlisted field 'key' with recognizable credentials is strictly redacted")
    void testAllowlistedKeyWithRecognizableCredentialRedacted() {
        String awsKey = "AKIAIOSFODNN7EXAMPLE";
        String slackToken = "xoxb-1234567890-abcdef1234";
        String gitlabToken = "glpat-abcdef12345678901234";
        String stripeKey = "sk_live_1234567890abcdef12345678";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", CANARY_API_KEY); // ghp_canary...
        metadata.put("safe_metric", 100);

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_UPDATED)
                .workspaceId(workspaceId)
                .metadata(metadata)
                .build();

        assertThat(event.getMetadata().get("key")).isEqualTo("[REDACTED_TOKEN]");
        assertThat(event.getMetadata().values()).doesNotContain(CANARY_API_KEY);

        // Test AWS, Slack, GitLab, Stripe credentials in generic 'key' field
        for (String cred : new String[]{awsKey, slackToken, gitlabToken, stripeKey}) {
            BaseDomainEvent credEvent = BaseDomainEvent.builder()
                    .eventType(EventType.SECRET_UPDATED)
                    .workspaceId(workspaceId)
                    .addMetadata("key", cred)
                    .build();

            assertThat(credEvent.getMetadata().get("key"))
                    .as("Recognizable credential %s in 'key' must be redacted", cred)
                    .isEqualTo("[REDACTED_TOKEN]");
            assertThat(credEvent.getMetadata().values()).doesNotContain(cred);
        }
    }

    @Test
    @DisplayName("P0 Redaction: Allowlisted field 'key' with secret-like value not matching regex is redacted via value-level checks")
    void testAllowlistedKeyWithSecretLikeValueNoRegexMatchRedacted() {
        // High-entropy 40-character hex crypto key (entropy = 3.92, does NOT match any of the legacy regexes)
        String rawCryptoHexKey = "4f8b2c1d9e3a7f6b5c0d8e1a2f3b4c5d6e7f8a9b";
        // High-entropy 48-character base64 secret
        String rawBase64Secret = "dGhpcy1pcy1hLXNlY3JldC1rZXktd2l0aC1lbnRyb3B5ISEh";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", rawCryptoHexKey);
        metadata.put("action", "ROTATE_ASYMMETRIC_KEY");

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_CREATED)
                .workspaceId(workspaceId)
                .metadata(metadata)
                .build();

        // Invariant: Generic safe name 'key' must NOT bypass value-level security check
        assertThat(event.getMetadata().get("key"))
                .as("Raw crypto hex key in 'key' must be redacted")
                .isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().values()).doesNotContain(rawCryptoHexKey);
        assertThat(event.getMetadata().get("action")).isEqualTo("ROTATE_ASYMMETRIC_KEY");

        // Base64 high-entropy secret in 'key'
        BaseDomainEvent b64Event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_CREATED)
                .workspaceId(workspaceId)
                .addMetadata("key", rawBase64Secret)
                .build();

        assertThat(b64Event.getMetadata().get("key"))
                .as("Base64 high entropy secret in 'key' must be redacted")
                .isEqualTo("[REDACTED]");
        assertThat(b64Event.getMetadata().values()).doesNotContain(rawBase64Secret);
    }

    @Test
    @DisplayName("P0 Redaction: Nested maps and lists with generic 'key' and high-entropy secret are recursively redacted")
    void testNestedMapsAndListsGenericKeyRedacted() {
        String rawCryptoHexKey = "4f8b2c1d9e3a7f6b5c0d8e1a2f3b4c5d6e7f8a9b";

        Map<String, Object> nestedDetails = new HashMap<>();
        nestedDetails.put("key", rawCryptoHexKey);
        nestedDetails.put("description", "Primary HSM key slot");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("validationdetails", java.util.List.of(
                nestedDetails,
                Map.of("key", "cache.metadata.v1", "note", "ghp_nestedGitHubTokenCanary1234567")
        ));

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.ROTATION_VALIDATED)
                .workspaceId(workspaceId)
                .metadata(metadata)
                .build();

        // Verify serialized JSON payload contains zero occurrences of hex key or token
        String serialized = "";
        try {
            serialized = objectMapper.writeValueAsString(event);
        } catch (Exception ignored) {}

        assertThat(serialized)
                .as("Serialized nested event must not contain raw crypto hex key")
                .doesNotContain(rawCryptoHexKey)
                .doesNotContain("ghp_nestedGitHubTokenCanary1234567");

        // Assert nested structure values
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) event.getMetadata().get("validationdetails");
        assertThat(list.get(0).get("key")).isEqualTo("[REDACTED]");
        assertThat(list.get(0).get("description")).isEqualTo("Primary HSM key slot");
        assertThat(list.get(1).get("key")).isEqualTo("cache.metadata.v1");
        assertThat(list.get(1).get("note")).isEqualTo("[REDACTED_TOKEN]");
    }

    @Test
    @DisplayName("P0 Redaction: Reject unallowlisted fields with secret-like or ambiguous sensitive payloads without echoing content")
    void testRejectUnknownAmbiguousSensitivePayloadsWithoutEchoing() {
        String rawCryptoHexKey = "4f8b2c1d9e3a7f6b5c0d8e1a2f3b4c5d6e7f8a9b";

        Map<String, Object> metadataWithUnknownSensitive = new HashMap<>();
        metadataWithUnknownSensitive.put("unrecognized_payload_data", rawCryptoHexKey);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            BaseDomainEvent.builder()
                    .eventType(EventType.SECRET_CREATED)
                    .workspaceId(workspaceId)
                    .metadata(metadataWithUnknownSensitive)
                    .build();
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejected without exposing content")
        .hasMessageNotContaining(rawCryptoHexKey)
        .hasMessageNotContaining("unrecognized_payload_data");

        // Test ambiguous token field
        Map<String, Object> metadataWithAmbiguousToken = new HashMap<>();
        metadataWithAmbiguousToken.put("ambiguous_session_token", "active-session-guid");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            BaseDomainEvent.builder()
                    .eventType(EventType.SECRET_CREATED)
                    .workspaceId(workspaceId)
                    .metadata(metadataWithAmbiguousToken)
                    .build();
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejected without exposing content")
        .hasMessageNotContaining("active-session-guid")
        .hasMessageNotContaining("ambiguous_session_token");
    }

    @Test
    @DisplayName("P0 Redaction: Exception messages and dead-letter records sanitize high-entropy hex keys")
    void testExceptionAndDeadLetterSanitizationForHexKeys() {
        String rawCryptoHexKey = "4f8b2c1d9e3a7f6b5c0d8e1a2f3b4c5d6e7f8a9b";
        String rawError = "Failed database handshake for key " + rawCryptoHexKey + " on host replica-01.internal:5432";

        String sanitized = com.secretvault.events.dispatcher.EventDispatcher.sanitizeErrorMessage(rawError);

        assertThat(sanitized)
                .as("Sanitized error must not contain raw crypto hex key")
                .doesNotContain(rawCryptoHexKey);
        assertThat(sanitized).contains("[REDACTED]");
        assertThat(sanitized).contains("replica-01.internal:5432");
    }
}
