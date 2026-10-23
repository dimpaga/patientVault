package com.example.patientvault.crypto.internal;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.patientvault.crypto.CryptoException;
import com.example.patientvault.crypto.CryptoService;
import com.example.patientvault.crypto.DataKey;
import com.example.patientvault.crypto.NewDataKey;
import com.example.patientvault.crypto.WrappedDataKey;
import com.google.crypto.tink.subtle.Random;

@Service
class DefaultCryptoService implements CryptoService {

    private static final int DEK_BYTES = 32;

    private final KeyRing keyRing;
    private final FieldCipher fieldCipher = new FieldCipher();
    private final StreamCipher streamCipher = new StreamCipher();
    private final DekCache dekCache;

    DefaultCryptoService(KeyRing keyRing, Clock clock) {
        this.keyRing = keyRing;
        this.dekCache = new DekCache(clock);
    }

    @Override
    public NewDataKey createDataKey(UUID ownerId) {
        byte[] raw = Random.randBytes(DEK_BYTES);
        try {
            DataKeyImpl key = new DataKeyImpl(ownerId, raw);
            WrappedDataKey wrapped = wrap(ownerId, raw);
            dekCache.put(ownerId, wrapped.bytes(), key);
            return new NewDataKey(key, wrapped);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    @Override
    public DataKey unwrapDataKey(UUID ownerId, byte[] wrapped) {
        DataKeyImpl cached = dekCache.get(ownerId, wrapped);
        if (cached != null) {
            return cached;
        }
        byte[] raw = Envelope.open(wrapped, keyRing::kek, Aad.forWrap(ownerId));
        try {
            DataKeyImpl key = new DataKeyImpl(ownerId, raw);
            dekCache.put(ownerId, wrapped, key);
            return key;
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    @Override
    public WrappedDataKey rewrapDataKey(UUID ownerId, byte[] wrapped) {
        DataKeyImpl key = impl(unwrapDataKey(ownerId, wrapped));
        byte[] raw = key.raw();
        try {
            WrappedDataKey rewrapped = wrap(ownerId, raw);
            dekCache.put(ownerId, rewrapped.bytes(), key);
            return rewrapped;
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    @Override
    public void forgetDataKey(UUID ownerId) {
        dekCache.evict(ownerId);
    }

    @Override
    public byte[] encrypt(DataKey key, String fieldName, byte[] plaintext) {
        return fieldCipher.encrypt(impl(key), fieldName, plaintext);
    }

    @Override
    public byte[] decrypt(DataKey key, String fieldName, byte[] ciphertext) {
        return fieldCipher.decrypt(impl(key), fieldName, ciphertext);
    }

    @Override
    public InputStream encryptStream(DataKey key, UUID fileId, String purpose, InputStream plaintext) {
        return streamCipher.encrypt(impl(key), fileId, purpose, plaintext);
    }

    @Override
    public InputStream decryptStream(DataKey key, UUID fileId, String purpose, InputStream ciphertext)
            throws IOException {
        return streamCipher.decrypt(impl(key), fileId, purpose, ciphertext);
    }

    @Override
    public long encryptedSize(long plaintextSize) {
        return streamCipher.encryptedSize(plaintextSize);
    }

    @Override
    public byte[] encryptSecret(String context, byte[] plaintext) {
        String keyId = keyRing.activeKeyId();
        try {
            return Envelope.seal(keyId, keyRing.userSecretKey(keyId), plaintext, Aad.forSecret(context));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    @Override
    public byte[] decryptSecret(String context, byte[] ciphertext) {
        return Envelope.open(ciphertext, keyRing::userSecretKey, Aad.forSecret(context));
    }

    private WrappedDataKey wrap(UUID ownerId, byte[] raw) {
        String keyId = keyRing.activeKeyId();
        try {
            return new WrappedDataKey(Envelope.seal(keyId, keyRing.kek(keyId), raw, Aad.forWrap(ownerId)), keyId);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Key wrapping failed", e);
        }
    }

    static DataKeyImpl impl(DataKey key) {
        if (key instanceof DataKeyImpl impl) {
            return impl;
        }
        throw new IllegalArgumentException("Unsupported data key implementation");
    }
}
