package com.example.patientvault.crypto.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.function.Function;

import com.example.patientvault.crypto.IntegrityFailureException;
import com.google.crypto.tink.Aead;

/**
 * Ciphertext container: {@code version(1) | keyIdLength(1) | keyId | nonce | ciphertext+tag}. The nonce
 * and tag are produced by Tink's AES-256-GCM; the header is authenticated by including it in the AAD,
 * so changing the key id or version makes decryption fail.
 */
final class Envelope {

    static final byte VERSION = 1;

    private Envelope() {
    }

    static byte[] seal(String keyId, Aead aead, byte[] plaintext, byte[] aad) throws GeneralSecurityException {
        byte[] header = header(keyId);
        byte[] body = aead.encrypt(plaintext, combine(header, aad));
        return ByteBuffer.allocate(header.length + body.length).put(header).put(body).array();
    }

    /** Reads the key id, selects the AEAD for it and decrypts. */
    static byte[] open(byte[] envelope, Function<String, Aead> aeadForKeyId, byte[] aad) {
        if (envelope == null || envelope.length < 3 || envelope[0] != VERSION) {
            throw new IntegrityFailureException("Ciphertext has an invalid format");
        }
        int idLength = envelope[1] & 0xFF;
        int bodyOffset = 2 + idLength;
        if (idLength == 0 || bodyOffset >= envelope.length) {
            throw new IntegrityFailureException("Ciphertext has an invalid format");
        }
        String keyId = new String(envelope, 2, idLength, StandardCharsets.UTF_8);
        Aead aead;
        try {
            aead = aeadForKeyId.apply(keyId);
        } catch (RuntimeException e) {
            throw new IntegrityFailureException("Ciphertext refers to an unavailable key");
        }
        byte[] header = Arrays.copyOfRange(envelope, 0, bodyOffset);
        byte[] body = Arrays.copyOfRange(envelope, bodyOffset, envelope.length);
        try {
            return aead.decrypt(body, combine(header, aad));
        } catch (GeneralSecurityException e) {
            throw new IntegrityFailureException("Ciphertext failed authentication", e);
        }
    }

    private static byte[] header(String keyId) {
        byte[] id = keyId.getBytes(StandardCharsets.UTF_8);
        if (id.length == 0 || id.length > 255) {
            throw new IllegalArgumentException("Invalid key id length");
        }
        return ByteBuffer.allocate(2 + id.length).put(VERSION).put((byte) id.length).put(id).array();
    }

    private static byte[] combine(byte[] header, byte[] aad) {
        return ByteBuffer.allocate(header.length + aad.length).put(header).put(aad).array();
    }
}
