package com.example.patientvault.identity.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ten single-use MFA recovery codes per user, stored only as Argon2id hashes. */
@Service
class RecoveryCodeService {

    static final int COUNT = 10;
    static final int LENGTH = 10;
    /** No 0/O/1/I/L to avoid transcription mistakes. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final RecoveryCodeRepository codes;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    RecoveryCodeService(RecoveryCodeRepository codes, PasswordEncoder encoder, Clock clock) {
        this.codes = codes;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Replaces all existing codes with a fresh set; returns the plaintext codes (shown once, formatted XXXXX-XXXXX). */
    @Transactional
    List<String> regenerate(UUID userId) {
        codes.deleteByUserId(userId);
        codes.flush();
        List<String> plain = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            String code = randomCode();
            codes.save(new RecoveryCode(userId, encoder.encode(code)));
            plain.add(code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2));
        }
        return plain;
    }

    /** Consumes the matching unused code, if any. */
    @Transactional
    boolean consume(UUID userId, String input) {
        String normalized = normalize(input);
        if (normalized.length() != LENGTH) {
            return false;
        }
        for (RecoveryCode candidate : codes.findByUserIdAndUsedAtIsNull(userId)) {
            if (encoder.matches(normalized, candidate.getCodeHash())) {
                candidate.markUsed(clock.instant());
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    int remaining(UUID userId) {
        return codes.findByUserIdAndUsedAtIsNull(userId).size();
    }

    static String normalize(String input) {
        return input == null ? "" : input.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
