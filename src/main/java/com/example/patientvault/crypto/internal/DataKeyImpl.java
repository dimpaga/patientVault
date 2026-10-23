package com.example.patientvault.crypto.internal;

import java.security.GeneralSecurityException;
import java.util.UUID;

import com.example.patientvault.crypto.CryptoException;
import com.example.patientvault.crypto.DataKey;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.subtle.AesGcmJce;

/** Unwrapped DEK. {@code toString()} prints the owner id only. */
final class DataKeyImpl implements DataKey {

    private final UUID ownerId;
    private final byte[] raw;
    private final Aead fieldAead;

    DataKeyImpl(UUID ownerId, byte[] raw) {
        this.ownerId = ownerId;
        this.raw = raw.clone();
        try {
            this.fieldAead = new AesGcmJce(this.raw);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Invalid data key", e);
        }
    }

    @Override
    public UUID ownerId() {
        return ownerId;
    }

    byte[] raw() {
        return raw.clone();
    }

    Aead fieldAead() {
        return fieldAead;
    }

    @Override
    public String toString() {
        return "DataKey[owner=" + ownerId + "]";
    }
}
