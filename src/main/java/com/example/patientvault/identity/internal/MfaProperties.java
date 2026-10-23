package com.example.patientvault.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.security.mfa.required}: when true every login needs a TOTP code and users must enrol at first login. */
@ConfigurationProperties(prefix = "app.security.mfa")
public record MfaProperties(@DefaultValue("true") boolean required) {
}
