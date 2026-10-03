package com.secretvault.rotation.model;

/**
 * Categorization of runtime secret consumer workloads.
 */
public enum ConsumerType {
    APPLICATION,
    SERVICE,
    WORKER,
    JOB,
    CONTAINER,
    CI_PIPELINE,
    CLI_PROCESS
}
