package com.secretvault.rotation.entity;

import com.secretvault.rotation.model.ValidationType;
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

/**
 * Persisted outcome of pre-activation validation tests performed on rotated secret values.
 */
@Entity
@Table(name = "rotation_validations")
public class RotationValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_type", nullable = false, length = 32)
    private ValidationType validationType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "validator_target", columnDefinition = "text")
    private String validatorTarget;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "validated_at")
    private Instant validatedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RotationValidation() {
    }

    public RotationValidation(UUID jobId, ValidationType validationType, String status, String validatorTarget, Integer responseCode, Long latencyMs, String errorMessage) {
        this.jobId = jobId;
        this.validationType = validationType;
        this.status = status;
        this.validatorTarget = validatorTarget;
        this.responseCode = responseCode;
        this.latencyMs = latencyMs;
        this.errorMessage = errorMessage;
        this.validatedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public ValidationType getValidationType() {
        return validationType;
    }

    public void setValidationType(ValidationType validationType) {
        this.validationType = validationType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getValidatorTarget() {
        return validatorTarget;
    }

    public void setValidatorTarget(String validatorTarget) {
        this.validatorTarget = validatorTarget;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
