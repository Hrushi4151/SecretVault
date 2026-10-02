package com.secretvault.oidc.entity;

import com.secretvault.oidc.model.OidcClaimOperator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oidc_claim_rules")
public class OidcClaimRule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "trust_policy_id", nullable = false)
    private UUID trustPolicyId;

    @Column(name = "claim_name", nullable = false, length = 128)
    private String claimName;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 32)
    private OidcClaimOperator operator = OidcClaimOperator.EQUALS;

    @Column(name = "expected_value", nullable = false, columnDefinition = "TEXT")
    private String expectedValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public OidcClaimRule() {
    }

    public OidcClaimRule(UUID trustPolicyId, String claimName, OidcClaimOperator operator, String expectedValue) {
        this.trustPolicyId = trustPolicyId;
        this.claimName = claimName;
        this.operator = operator != null ? operator : OidcClaimOperator.EQUALS;
        this.expectedValue = expectedValue;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTrustPolicyId() {
        return trustPolicyId;
    }

    public void setTrustPolicyId(UUID trustPolicyId) {
        this.trustPolicyId = trustPolicyId;
    }

    public String getClaimName() {
        return claimName;
    }

    public void setClaimName(String claimName) {
        this.claimName = claimName;
    }

    public OidcClaimOperator getOperator() {
        return operator;
    }

    public void setOperator(OidcClaimOperator operator) {
        this.operator = operator;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
