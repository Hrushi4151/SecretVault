package com.secretvault.oidc.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "oidc_trust_policies")
public class OidcTrustPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "machine_identity_id", nullable = false)
    private UUID machineIdentityId;

    @Column(name = "oidc_provider_id", nullable = false)
    private UUID oidcProviderId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "trustPolicyId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OidcClaimRule> claimRules = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public OidcTrustPolicy() {
    }

    public OidcTrustPolicy(UUID workspaceId, UUID machineIdentityId, UUID oidcProviderId,
                           String name, String description, boolean enabled, int priority, UUID createdBy) {
        this.workspaceId = workspaceId;
        this.machineIdentityId = machineIdentityId;
        this.oidcProviderId = oidcProviderId;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.priority = priority;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addClaimRule(OidcClaimRule rule) {
        rule.setTrustPolicyId(this.id);
        this.claimRules.add(rule);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getMachineIdentityId() {
        return machineIdentityId;
    }

    public void setMachineIdentityId(UUID machineIdentityId) {
        this.machineIdentityId = machineIdentityId;
    }

    public UUID getOidcProviderId() {
        return oidcProviderId;
    }

    public void setOidcProviderId(UUID oidcProviderId) {
        this.oidcProviderId = oidcProviderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public List<OidcClaimRule> getClaimRules() {
        return claimRules;
    }

    public void setClaimRules(List<OidcClaimRule> claimRules) {
        this.claimRules = claimRules;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
