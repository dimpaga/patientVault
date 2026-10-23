package com.example.patientvault.crypto.internal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.UUID;

import com.example.patientvault.crypto.CryptoException;
import com.example.patientvault.crypto.IntegrityFailureException;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;
import com.google.crypto.tink.subtle.Hkdf;
import com.google.crypto.tink.subtle.Random;

/**
 * Streaming AEAD ({@code AES256_GCM_HKDF_1MB}) for files and thumbnails. The per-file key is derived
 * from the patient DEK with HKDF (salt = file id, info = purpose); associated data binds the stream to
 * owner, file and purpose.
 */
final class StreamCipher {

    static final int SEGMENT_SIZE = 1 << 20;
    private static final int KEY_BYTES = 32;

    /** Instance used only for {@code expectedCiphertextSize}, which depends on parameters, not on the key. */
    private final AesGcmHkdfStreaming sizing = newStreaming(Random.randBytes(KEY_BYTES));

    InputStream encrypt(DataKeyImpl key, UUID fileId, String purpose, InputStream plaintext) {
        StreamingAead aead = streamingFor(key, fileId, purpose);
        byte[] aad = aad(key, fileId, purpose);
        try {
            return new EncryptingInputStream(plaintext, sink -> aead.newEncryptingStream(sink, aad));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InputStream decrypt(DataKeyImpl key, UUID fileId, String purpose, InputStream ciphertext) throws IOException {
        StreamingAead aead = streamingFor(key, fileId, purpose);
        try {
            return new IntegrityMappingInputStream(aead.newDecryptingStream(ciphertext, aad(key, fileId, purpose)));
        } catch (GeneralSecurityException e) {
            throw new IntegrityFailureException("File ciphertext failed authentication", e);
        } catch (IOException e) {
            throw mapIntegrity(e);
        }
    }

    long encryptedSize(long plaintextSize) {
        return sizing.expectedCiphertextSize(plaintextSize);
    }

    private StreamingAead streamingFor(DataKeyImpl key, UUID fileId, String purpose) {
        byte[] dek = key.raw();
        byte[] derived = null;
        try {
            byte[] salt = uuidBytes(fileId);
            byte[] info = ("patientvault/file/" + purpose + "/v1").getBytes(StandardCharsets.UTF_8);
            derived = Hkdf.computeHkdf(KeyRing.HKDF_HASH, dek, salt, info, KEY_BYTES);
            return newStreaming(derived);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("File key derivation failed", e);
        } finally {
            Arrays.fill(dek, (byte) 0);
            if (derived != null) {
                Arrays.fill(derived, (byte) 0);
            }
        }
    }

    private static AesGcmHkdfStreaming newStreaming(byte[] ikm) {
        try {
            return new AesGcmHkdfStreaming(ikm, KeyRing.HKDF_HASH, KEY_BYTES, SEGMENT_SIZE, 0);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Streaming cipher initialisation failed", e);
        }
    }

    private static byte[] aad(DataKeyImpl key, UUID fileId, String purpose) {
        byte[] base = Aad.forFile(key.ownerId(), fileId);
        byte[] suffix = purpose.getBytes(StandardCharsets.UTF_8);
        byte[] out = Arrays.copyOf(base, base.length + suffix.length);
        System.arraycopy(suffix, 0, out, base.length, suffix.length);
        return out;
    }

    private static byte[] uuidBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    /** Authentication failures surface from Tink as IOException with a security cause; turn them into integrity failures. */
    private static IOException mapIntegrity(IOException e) {
        if (isIntegrity(e)) {
            throw new IntegrityFailureException("File ciphertext failed authentication", e);
        }
        return e;
    }

    private static boolean isIntegrity(IOException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof GeneralSecurityException) {
                return true;
            }
        }
        return false;
    }

    private static final class IntegrityMappingInputStream extends FilterInputStream {

        IntegrityMappingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (IOException e) {
                throw mapIntegrity(e);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return super.read(b, off, len);
            } catch (IOException e) {
                throw mapIntegrity(e);
            }
        }
    }
}
