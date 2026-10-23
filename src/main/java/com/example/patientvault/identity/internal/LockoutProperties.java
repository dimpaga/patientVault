package com.example.patientvault.identity.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Account lockout: after {@code maxAttempts} consecutive failures the account is locked for
 * {@code duration}; every further block of {@code maxAttempts} failures doubles it up to {@code maxDuration}.
 */
@ConfigurationProperties(prefix = "app.security.lockout")
public record LockoutProperties(
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15m") Duration duration,
        @DefaultValue("8h") Duration maxDuration) {
}
