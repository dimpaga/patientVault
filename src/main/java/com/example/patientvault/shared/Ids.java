package com.example.patientvault.shared;

import java.security.SecureRandom;
import java.util.UUID;

/** Identifier generation. UUIDv7 keeps primary-key indexes roughly time-ordered. */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static UUID newId() {
        long millis = System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL;
        long msb = (millis << 16) | 0x7000L | (RANDOM.nextLong() & 0x0FFFL);
        long lsb = 0x8000_0000_0000_0000L | (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL);
        return new UUID(msb, lsb);
    }
}
