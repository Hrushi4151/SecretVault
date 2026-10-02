package com.secretvault.security.finding.model;

/**
 * Canonical enumeration of deterministic security finding categories in SecretVault.
 */
public enum FindingCategory {
    EXCESSIVE_PRIVILEGE(
            "Excessive Privilege Assignment",
            "Actor possesses overly broad access across multiple unneeded projects or environments without business justification."
    ),
    PRIVILEGE_ESCALATION_PATTERN(
            "Privilege Escalation Pattern",
            "Suspicious sequence of rapid role or grant elevations followed by immediate sensitive secret reveals."
    ),
    SUSPICIOUS_JIT_ACTIVITY(
            "Suspicious Just-In-Time Activity",
            "Unusual rate of repeated temporary access elevations, anomalous approver pairs, or out-of-band requests."
    ),
    REPEATED_AUTHORIZATION_FAILURES(
            "Repeated Authorization Denials",
            "High frequency of access denials indicating enumeration attempts, misconfiguration, or unauthorized probing."
    ),
    UNUSUAL_ADMIN_ACTIVITY(
            "Unusual Administrative Mutation Spike",
            "Sudden concentrated volume of membership, policy, or role mutations by a single administrative actor."
    ),
    DORMANT_PRIVILEGED_ACCESS(
            "Dormant Privileged Account",
            "High-privilege account holding standing administrative access with zero observed activity for over 30 days."
    ),
    ACCESS_REVIEW_OVERDUE(
            "Access Review Campaign Overdue",
            "Active access certification campaign has surpassed its deadline or contains unresolved items."
    ),
    UNUSED_GRANULAR_GRANT(
            "Stale / Unused Granular Grant",
            "Explicit granular resource permission grant has remained unexercised for over 14 days."
    ),
    ACCESS_CONCENTRATION(
            "Access Concentration Risk",
            "Single user holds exclusive administrative control with zero secondary reviewers or backup administrators."
    ),
    AUTHENTICATION_ANOMALY(
            "Authentication Anomaly",
            "Multiple consecutive authentication failures, rapid IP transitions, or account lockout alerts."
    ),
    PROVIDER_DRIFT_DETECTED(
            "External Provider Secret Drift",
            "Discrepancy detected between desired SecretVault state and actual provider deployment state."
    ),
    PROVIDER_CREDENTIAL_FAILURE(
            "External Provider Credential Failure",
            "External provider credentials rejected, invalid, or lacking necessary permissions."
    );

    private final String displayName;
    private final String description;

    FindingCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
