package com.example.patientvault.identity.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

/** RFC 6238 TOTP (SHA-1, 6 digits, 30 s) with a +/-1 step tolerance and replay protection. */
@Component
class TotpService {

    static final String ISSUER = "PatientVault";
    static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SECRET_BYTES = 20;

    private final SecureRandom random = new SecureRandom();
    private final TimeBasedOneTimePasswordGenerator generator;

    TotpService() {
        this.generator = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(PERIOD_SECONDS), DIGITS);
    }

    String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    String otpauthUri(String username, String secret) {
        String label = URLEncoder.encode(ISSUER + ":" + username, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    static boolean looksLikeTotp(String code) {
        return code != null && code.length() == DIGITS && code.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * @param lastUsedStep the last accepted time step, or null; steps at or before it are rejected so a
     *                     code can never be used twice
     * @return the matching time step, or -1 when the code is invalid
     */
    long verify(String secret, String code, Instant now, Long lastUsedStep) {
        if (!looksLikeTotp(code)) {
            return -1;
        }
        try {
            SecretKeySpec key = new SecretKeySpec(Base32.decode(secret), generator.getAlgorithm());
            long current = now.getEpochSecond() / PERIOD_SECONDS;
            byte[] presented = code.getBytes(StandardCharsets.US_ASCII);
            long match = -1;
            for (long step = current - 1; step <= current + 1; step++) {
                if (lastUsedStep != null && step <= lastUsedStep) {
                    continue;
                }
                int expected = generator.generateOneTimePassword(key, Instant.ofEpochSecond(step * PERIOD_SECONDS));
                byte[] expectedBytes = String.format("%0" + DIGITS + "d", expected).getBytes(StandardCharsets.US_ASCII);
                if (MessageDigest.isEqual(expectedBytes, presented)) {
                    match = step;
                }
            }
            return match;
        } catch (InvalidKeyException | IllegalArgumentException e) {
            return -1;
        }
    }
}
