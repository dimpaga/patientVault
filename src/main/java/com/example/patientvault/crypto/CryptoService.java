package com.example.patientvault.crypto;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Public facade of the crypto module. All patient data is encrypted with a per-patient data key
 * (DEK); the DEK is stored wrapped by a master-key-derived KEK, so deleting the wrapped DEK
 * crypto-shreds everything encrypted with it.
 */
public interface CryptoService {

    /** Generates a new random DEK for the owner and wraps it with the active KEK. */
    NewDataKey createDataKey(UUID ownerId);

    /** Unwraps a stored DEK (short-lived in-memory cache). Throws {@link IntegrityFailureException} if it does not verify. */
    DataKey unwrapDataKey(UUID ownerId, byte[] wrapped);

    /** Re-wraps a stored DEK with the currently active KEK (key rotation). */
    WrappedDataKey rewrapDataKey(UUID ownerId, byte[] wrapped);

    /** Drops any cached copy of the owner's DEK; call when the owner is erased. */
    void forgetDataKey(UUID ownerId);

    /** Encrypts a field value; associated data binds the ciphertext to the owner and field name. */
    byte[] encrypt(DataKey key, String fieldName, byte[] plaintext);

    byte[] decrypt(DataKey key, String fieldName, byte[] ciphertext);

    default byte[] encryptText(DataKey key, String fieldName, String plaintext) {
        return encrypt(key, fieldName, plaintext.getBytes(StandardCharsets.UTF_8));
    }

    default String decryptText(DataKey key, String fieldName, byte[] ciphertext) {
        return new String(decrypt(key, fieldName, ciphertext), StandardCharsets.UTF_8);
    }
}
