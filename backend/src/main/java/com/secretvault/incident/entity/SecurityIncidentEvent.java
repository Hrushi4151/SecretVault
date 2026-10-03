package com.secretvault.incident.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_incident_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_incident_event", columnNames = {"incident_id", "event_id"})
})
public class SecurityIncidentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 32)
    private IncidentRelationshipType relationshipType = IncidentRelationshipType.CORRELATED;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SecurityIncidentEvent() {}

    public SecurityIncidentEvent(UUID incidentId, UUID eventId, IncidentRelationshipType relationshipType, String notes) {
        this.incidentId = incidentId;
        this.eventId = eventId;
        this.relationshipType = relationshipType;
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public IncidentRelationshipType getRelationshipType() { return relationshipType; }
    public void setRelationshipType(IncidentRelationshipType relationshipType) { this.relationshipType = relationshipType; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
}
