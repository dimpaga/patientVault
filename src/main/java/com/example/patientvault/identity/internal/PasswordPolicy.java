package com.example.patientvault.identity.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Length of at least 12 characters, not a known common password, not containing the username. No composition rules. */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    private final Set<String> common = load();

    /** @return message keys of violated rules; empty when the password is acceptable */
    public List<String> violations(String password, String username) {
        List<String> violations = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            violations.add("password.policy.length");
            return violations;
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (common.contains(lower)) {
            violations.add("password.policy.common");
        }
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            violations.add("password.policy.username");
        }
        return violations;
    }

    private static Set<String> load() {
        try (InputStream in = new ClassPathResource("identity/common-passwords.txt").getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
