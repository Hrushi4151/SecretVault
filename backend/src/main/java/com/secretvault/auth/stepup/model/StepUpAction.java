package com.secretvault.auth.stepup.model;

/**
 * Enumeration of sensitive operations eligible for Step-Up Authentication.
 */
public enum StepUpAction {
    SECRET_REVEAL,
    SECRET_DELETE,
    SECRET_ROLLBACK,
    ENVIRONMENT_PROMOTE,
    ACCESS_GRANT,
    ACCESS_REVOKE,
    JIT_APPROVE,
    MFA_DISABLE,
    SESSION_REVOKE_ALL
}
