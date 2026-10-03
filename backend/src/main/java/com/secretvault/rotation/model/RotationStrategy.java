package com.secretvault.rotation.model;

/**
 * Strategy defining how a secret rotation is triggered.
 */
public enum RotationStrategy {
    MANUAL,
    SCHEDULED,
    ON_DEMAND,
    EXPIRY_BASED,
    PROVIDER_DRIVEN,
    EVENT_DRIVEN,
    EMERGENCY
}
