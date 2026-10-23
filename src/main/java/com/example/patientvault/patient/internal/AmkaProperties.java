package com.example.patientvault.patient.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.validation.amka.strict-date}: treat an unparsable DDMMYY prefix as an error instead of a warning. */
@ConfigurationProperties(prefix = "app.validation.amka")
public record AmkaProperties(@DefaultValue("false") boolean strictDate) {
}
