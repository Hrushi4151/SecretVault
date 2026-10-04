package com.secretvault.ai.domain.model;

/**
 * Standard classification of AI user inquiries and analytical workloads.
 */
public enum AiIntentType {
    GENERAL_QUERY,
    DRIFT_RCA,
    DEPLOYMENT_RCA,
    SYNC_RCA,
    POSTURE_FORECAST,
    RECOMMENDATION_TRIAGE,
    BLAST_RADIUS_INQUIRY
}
