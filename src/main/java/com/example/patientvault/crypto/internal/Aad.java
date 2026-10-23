package com.example.patientvault.crypto.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Builds associated-data values so ciphertext cannot be moved between owners, fields or files. */
final class Aad {

    private Aad() {
    }

    /** {@code ownerId || fieldName} */
    static byte[] forField(UUID ownerId, String fieldName) {
        return concat(ownerId, fieldName.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code ownerId || purpose} used for DEK wrapping. */
    static byte[] forWrap(UUID ownerId) {
        return concat(ownerId, "dek-wrap".getBytes(StandardCharsets.UTF_8));
    }

    /** {@code ownerId || fileId} used for file streams. */
    static byte[] forFile(UUID ownerId, UUID fileId) {
        return ByteBuffer.allocate(32)
                .putLong(ownerId.getMostSignificantBits()).putLong(ownerId.getLeastSignificantBits())
                .putLong(fileId.getMostSignificantBits()).putLong(fileId.getLeastSignificantBits())
                .array();
    }

    static byte[] forSecret(String context) {
        return ("secret:" + context).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(UUID id, byte[] suffix) {
        return ByteBuffer.allocate(16 + suffix.length)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .put(suffix)
                .array();
    }
}
