package com.secretvault.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enforce Redis-backed distributed rate limiting on REST endpoints.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    /**
     * Unique category identifier (e.g. "auth_login", "secret_reveal", "jit_request").
     */
    String category();

    /**
     * Maximum allowed requests in the time window.
     */
    int limit() default 10;

    /**
     * Time window in seconds.
     */
    int windowSeconds() default 60;

    /**
     * Identification strategy (IP, USER_ID, IP_AND_USER, WORKSPACE_ID, GLOBAL).
     */
    RateLimitIdentifierType type() default RateLimitIdentifierType.IP;

    /**
     * Custom message returned in standard API error response when exceeded.
     */
    String message() default "Too many requests. Please try again later.";
}
