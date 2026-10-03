package io.secretvault.starter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a decrypted secret value directly from SecretVault into a Spring-managed bean.
 * Supports automated runtime refresh if the secret version rotates.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SecretVaultValue {

    /**
     * Secret key name (e.g. "DB_PASSWORD" or "API_KEY").
     */
    String value();

    /**
     * If true, updates bean field dynamically when secret rotates without restarting the JVM.
     */
    boolean autoRefresh() default true;
}
