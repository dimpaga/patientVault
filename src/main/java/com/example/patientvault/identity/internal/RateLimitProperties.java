package com.example.patientvault.identity.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Per-IP limit for POST /login and POST /login/mfa: {@code attempts} requests per {@code window}. */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        @DefaultValue("10") int attempts,
        @DefaultValue("1m") Duration window) {
}
