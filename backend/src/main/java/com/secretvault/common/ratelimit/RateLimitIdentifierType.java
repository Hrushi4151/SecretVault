package com.secretvault.common.ratelimit;

/**
 * Dimension used to compute the rate limit identifier.
 */
public enum RateLimitIdentifierType {
    /**
     * Rate limit by client remote IP address (considers X-Forwarded-For).
     */
    IP,

    /**
     * Rate limit by authenticated User ID.
     */
    USER_ID,

    /**
     * Rate limit by combined IP address and User ID.
     */
    IP_AND_USER,

    /**
     * Rate limit by target Workspace ID from request path.
     */
    WORKSPACE_ID,

    /**
     * Global category rate limit across all callers.
     */
    GLOBAL
}
