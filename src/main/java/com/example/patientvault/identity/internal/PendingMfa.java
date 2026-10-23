package com.example.patientvault.identity.internal;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * State between the password step and the second factor. The session is NOT authenticated while this
 * exists; it only allows access to the MFA pages.
 *
 * @param enrol true when the user has no TOTP secret yet and must enrol first
 */
public record PendingMfa(UUID userId, String username, Instant issuedAt, boolean enrol) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final Duration VALIDITY = Duration.ofMinutes(5);

    public boolean isExpired(Instant now) {
        return now.isAfter(issuedAt.plus(VALIDITY));
    }

    @Override
    public String toString() {
        return "PendingMfa[userId=" + userId + "]";
    }
}
