package com.secretvault.ai.domain.model;

/**
 * Standard classification of AI user inquiries and analytical workloads.
 */
public enum AiIntentType {
    // Standard Enterprise Intent Classifications
    COPILOT_GENERAL,
    SECURITY_POSTURE,
    SECURITY_FINDING,
    DEPLOYMENT_RCA,
    SYNC_FAILURE,
    ROTATION_ANALYSIS,
    SECRET_HEALTH,
    BLAST_RADIUS,
    REMEDIATION_RECOMMENDATION,
    REMEDIATION_PLAN,
    SYSTEM_HEALTH,
    HELP,
    UNKNOWN,

    // Legacy & Alias Classifications for Backward Compatibility
    GENERAL_QUERY,
    DRIFT_RCA,
    SYNC_RCA,
    POSTURE_FORECAST,
    RECOMMENDATION_TRIAGE,
    BLAST_RADIUS_INQUIRY;

    public static AiIntentType fromString(String name) {
        if (name == null || name.isBlank()) {
            return COPILOT_GENERAL;
        }
        String clean = name.trim().toUpperCase();
        try {
            return AiIntentType.valueOf(clean);
        } catch (IllegalArgumentException e) {
            // Map legacy / alternative names
            return switch (clean) {
                case "GENERAL_QUERY", "GENERAL", "CHAT" -> COPILOT_GENERAL;
                case "POSTURE_FORECAST", "POSTURE", "SECURITY_AUDIT" -> SECURITY_POSTURE;
                case "FINDING", "VULNERABILITY" -> SECURITY_FINDING;
                case "DEPLOYMENT", "DEPLOY", "RCA" -> DEPLOYMENT_RCA;
                case "SYNC_RCA", "SYNC", "DRIFT", "DRIFT_RCA", "DRIFT_ANALYSIS" -> SYNC_FAILURE;
                case "ROTATION", "ROTATE", "LEASE" -> ROTATION_ANALYSIS;
                case "SECRET", "CREDENTIAL", "METADATA" -> SECRET_HEALTH;
                case "BLAST_RADIUS_INQUIRY", "IMPACT" -> BLAST_RADIUS;
                case "RECOMMENDATION_TRIAGE", "RECOMMENDATION", "RECOMMEND" -> REMEDIATION_RECOMMENDATION;
                case "PLAN", "PLAN_GENERATION" -> REMEDIATION_PLAN;
                case "SYSTEM", "STATUS", "HEALTH" -> SYSTEM_HEALTH;
                case "HELP", "INFO", "COMMANDS" -> HELP;
                default -> UNKNOWN;
            };
        }
    }
}
