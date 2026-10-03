package com.secretvault.auth.webauthn.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a registered WebAuthn / FIDO2 Passkey or Security Key credential.
 * Contains only public verification key material and authenticator state.
 * NEVER stores private keys.
 */
@Entity
@Table(name = "user_webauthn_credentials")
public class UserWebAuthnCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_id", nullable = false, unique = true, length = 500)
    private String credentialId;

    @Column(name = "public_key_cose", nullable = false)
    private byte[] publicKeyCose;

    @Column(name = "sign_count", nullable = false)
    private Long signCount = 0L;

    @Column(name = "aaguid", length = 64)
    private String aaguid;

    @Column(name = "attestation_format", length = 64)
    private String attestationFormat = "none";

    @Column(name = "transports", length = 255)
    private String transports;

    @Column(name = "user_verified_capable", nullable = false)
    private Boolean userVerifiedCapable = true;

    @Column(name = "backup_eligible", nullable = false)
    private Boolean backupEligible = false;

    @Column(name = "backup_state", nullable = false)
    private Boolean backupState = false;

    @Column(name = "discoverable", nullable = false)
    private Boolean discoverable = true;

    @Column(name = "friendly_name", nullable = false, length = 100)
    private String friendlyName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "last_used_ip", length = 64)
    private String lastUsedIp;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 255)
    private String revocationReason;

    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public UserWebAuthnCredential() {
    }

    public UserWebAuthnCredential(
            UUID userId,
            String credentialId,
            byte[] publicKeyCose,
            Long signCount,
            String aaguid,
            String attestationFormat,
            String transports,
            Boolean userVerifiedCapable,
            Boolean backupEligible,
            Boolean backupState,
            Boolean discoverable,
            String friendlyName
    ) {
        this.userId = userId;
        this.credentialId = credentialId;
        this.publicKeyCose = publicKeyCose;
        this.signCount = signCount != null ? signCount : 0L;
        this.aaguid = aaguid;
        this.attestationFormat = attestationFormat != null ? attestationFormat : "none";
        this.transports = transports;
        this.userVerifiedCapable = userVerifiedCapable != null ? userVerifiedCapable : true;
        this.backupEligible = backupEligible != null ? backupEligible : false;
        this.backupState = backupState != null ? backupState : false;
        this.discoverable = discoverable != null ? discoverable : true;
        this.friendlyName = friendlyName;
        this.createdAt = Instant.now();
        this.version = 0L;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive() {
        return !isRevoked();
    }

    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revocationReason = reason;
    }

    public void recordUsage(Long newSignCount, String ipAddress) {
        if (newSignCount != null) {
            this.signCount = newSignCount;
        }
        this.lastUsedAt = Instant.now();
        this.lastUsedIp = ipAddress;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public byte[] getPublicKeyCose() {
        return publicKeyCose;
    }

    public void setPublicKeyCose(byte[] publicKeyCose) {
        this.publicKeyCose = publicKeyCose;
    }

    public Long getSignCount() {
        return signCount;
    }

    public void setSignCount(Long signCount) {
        this.signCount = signCount;
    }

    public String getAaguid() {
        return aaguid;
    }

    public void setAaguid(String aaguid) {
        this.aaguid = aaguid;
    }

    public String getAttestationFormat() {
        return attestationFormat;
    }

    public void setAttestationFormat(String attestationFormat) {
        this.attestationFormat = attestationFormat;
    }

    public String getTransports() {
        return transports;
    }

    public void setTransports(String transports) {
        this.transports = transports;
    }

    public Boolean getUserVerifiedCapable() {
        return userVerifiedCapable;
    }

    public void setUserVerifiedCapable(Boolean userVerifiedCapable) {
        this.userVerifiedCapable = userVerifiedCapable;
    }

    public Boolean getBackupEligible() {
        return backupEligible;
    }

    public void setBackupEligible(Boolean backupEligible) {
        this.backupEligible = backupEligible;
    }

    public Boolean getBackupState() {
        return backupState;
    }

    public void setBackupState(Boolean backupState) {
        this.backupState = backupState;
    }

    public Boolean getDiscoverable() {
        return discoverable;
    }

    public void setDiscoverable(Boolean discoverable) {
        this.discoverable = discoverable;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public String getLastUsedIp() {
        return lastUsedIp;
    }

    public void setLastUsedIp(String lastUsedIp) {
        this.lastUsedIp = lastUsedIp;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserWebAuthnCredential that = (UserWebAuthnCredential) o;
        return Objects.equals(id, that.id) || Objects.equals(credentialId, that.credentialId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, credentialId);
    }
}
