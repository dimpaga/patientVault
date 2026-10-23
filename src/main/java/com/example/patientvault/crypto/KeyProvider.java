package com.example.patientvault.crypto;

import java.util.Set;

/**
 * Source of the versioned 256-bit master keys. The environment-based implementation can be replaced
 * by a managed KMS later without touching the rest of the crypto module.
 */
public interface KeyProvider {

    /** Id of the key used for all new wrapping/encryption. */
    String activeKeyId();

    /** Id of the key from which the blind-index (search token) key is derived. */
    String blindIndexKeyId();

    /** All key ids available for decryption. */
    Set<String> keyIds();

    /** Returns a copy of the raw master key; throws {@link CryptoException} for an unknown id. */
    byte[] masterKey(String keyId);
}
