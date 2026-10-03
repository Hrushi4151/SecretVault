package com.secretvault.repository.model;

public enum RemediationAction {
    MARK_FALSE_POSITIVE,
    IGNORE,
    ROTATE_SECRET,
    REVOKE_SECRET,
    REWRITE_HISTORY,
    CREATE_SECRET_VAULT_REFERENCE,
    CREATE_ACCESS_REVIEW
}
