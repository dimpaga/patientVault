package com.example.patientvault.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method (or class) as a sensitive action: the doctor must have authenticated or
 * re-authenticated (password + TOTP) within {@code app.security.reauth-window}, otherwise the request is
 * redirected to the re-authentication page. Used for patient deletion, password/MFA changes, audit export.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRecentAuth {
}
