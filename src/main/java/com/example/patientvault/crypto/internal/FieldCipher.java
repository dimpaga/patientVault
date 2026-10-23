package com.example.patientvault.crypto.internal;

import java.security.GeneralSecurityException;

import com.example.patientvault.crypto.CryptoException;

/** Field-level AES-256-GCM encryption with the patient DEK; associated data = ownerId || fieldName. */
final class FieldCipher {

    /** Header key id used for values encrypted directly with a DEK (the DEK itself carries no version). */
    static final String DEK_KEY_LABEL = "dek";

    byte[] encrypt(DataKeyImpl key, String fieldName, byte[] plaintext) {
        try {
            return Envelope.seal(DEK_KEY_LABEL, key.fieldAead(), plaintext, Aad.forField(key.ownerId(), fieldName));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    byte[] decrypt(DataKeyImpl key, String fieldName, byte[] ciphertext) {
        return Envelope.open(ciphertext, id -> {
            if (!DEK_KEY_LABEL.equals(id)) {
                throw new IllegalArgumentException("unexpected key label");
            }
            return key.fieldAead();
        }, Aad.forField(key.ownerId(), fieldName));
    }
}
