package com.secretvault.events;

import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 13: BaseDomainEvent Redaction & Contract Tests")
class BaseDomainEventTest {

    @Test
    @DisplayName("Mandatory workspaceId and eventType validation")
    void testMandatoryFields() {
        assertThatThrownBy(() -> BaseDomainEvent.builder().build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BaseDomainEvent.builder().workspaceId(UUID.randomUUID()).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Sensitive fields are strictly redacted in event metadata")
    void testSensitiveMetadataRedaction() {
        UUID workspaceId = UUID.randomUUID();
        Map<String, Object> rawMeta = new HashMap<>();
        rawMeta.put("safeField", "safeValue");
        rawMeta.put("plaintext", "mySuperSecretPassword123");
        rawMeta.put("secretValue", "super-secret");
        rawMeta.put("dek", "raw-encryption-key-data");
        rawMeta.put("apiKey", "sk_live_123456789");

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_UPDATED)
                .workspaceId(workspaceId)
                .severity(EventSeverity.INFO)
                .metadata(rawMeta)
                .build();

        assertThat(event.getMetadata().get("safeField")).isEqualTo("safeValue");
        assertThat(event.getMetadata().get("plaintext")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("secretValue")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("dek")).isEqualTo("[REDACTED]");
        assertThat(event.getMetadata().get("apiKey")).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("Event immutability contract")
    void testImmutability() {
        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_CREATED)
                .workspaceId(UUID.randomUUID())
                .addMetadata("key", "val")
                .build();

        assertThatThrownBy(() -> event.getMetadata().put("newKey", "newVal"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
