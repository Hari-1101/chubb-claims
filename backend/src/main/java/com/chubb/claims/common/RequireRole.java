package com.chubb.claims.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a controller (class level) or a single endpoint (method level, overrides the class) to the given roles.
 * Checked by {@link IdentityInterceptor}; callers with another role get 403.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    UserRole[] value();
}
