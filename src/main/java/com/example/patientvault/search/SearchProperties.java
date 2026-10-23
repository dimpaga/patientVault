package com.example.patientvault.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Name search works on word prefixes of {@code minPrefixLength..maxPrefixLength} characters. */
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        @DefaultValue("3") int minPrefixLength,
        @DefaultValue("15") int maxPrefixLength) {
}
