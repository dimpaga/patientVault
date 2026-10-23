package com.example.patientvault.crypto.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.example.patientvault.crypto.CryptoException;
import com.example.patientvault.crypto.KeyProvider;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.Mac;
import com.google.crypto.tink.subtle.AesGcmJce;
import com.google.crypto.tink.subtle.Hkdf;
import com.google.crypto.tink.subtle.PrfHmacJce;
import com.google.crypto.tink.subtle.PrfMac;

/**
 * Derives the purpose-specific keys from each master key with HKDF-SHA256 and distinct info labels:
 * a KEK (wraps patient DEKs), a user-secret key (TOTP secret, notification email) and, for the
 * configured blind-index key id only, the HMAC key for search tokens. Runs a self-check at startup.
 */
@Component
class KeyRing {

    static final String HKDF_HASH = "HmacSha256";
    private static final byte[] LABEL_KEK = label("kek");
    private static final byte[] LABEL_USER_SECRET = label("user-secret");
    private static final byte[] LABEL_BLIND_INDEX = label("blind-index");
    private static final int DERIVED_KEY_BYTES = 32;

    private final String activeKeyId;
    private final Map<String, Aead> keks = new HashMap<>();
    private final Map<String, Aead> userSecretKeys = new HashMap<>();
    private final Mac blindIndexMac;

    KeyRing(KeyProvider provider) {
        this.activeKeyId = provider.activeKeyId();
        try {
            for (String keyId : provider.keyIds()) {
                byte[] master = provider.masterKey(keyId);
                try {
                    keks.put(keyId, new AesGcmJce(derive(master, LABEL_KEK)));
                    userSecretKeys.put(keyId, new AesGcmJce(derive(master, LABEL_USER_SECRET)));
                } finally {
                    Arrays.fill(master, (byte) 0);
                }
            }
            byte[] blindMaster = provider.masterKey(provider.blindIndexKeyId());
            try {
                byte[] blindKey = derive(blindMaster, LABEL_BLIND_INDEX);
                this.blindIndexMac = new PrfMac(new PrfHmacJce("HMACSHA256", new SecretKeySpec(blindKey, "HMAC")), 32);
                Arrays.fill(blindKey, (byte) 0);
            } finally {
                Arrays.fill(blindMaster, (byte) 0);
            }
            selfCheck();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key ring initialisation failed", e);
        }
    }

    private static byte[] label(String purpose) {
        return ("patientvault/" + purpose + "/v1").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] derive(byte[] master, byte[] info) throws GeneralSecurityException {
        return Hkdf.computeHkdf(HKDF_HASH, master, new byte[0], info, DERIVED_KEY_BYTES);
    }

    String activeKeyId() {
        return activeKeyId;
    }

    Set<String> keyIds() {
        return Collections.unmodifiableSet(keks.keySet());
    }

    Aead kek(String keyId) {
        return require(keks, keyId);
    }

    Aead userSecretKey(String keyId) {
        return require(userSecretKeys, keyId);
    }

    Mac blindIndexMac() {
        return blindIndexMac;
    }

    private static Aead require(Map<String, Aead> map, String keyId) {
        Aead aead = map.get(keyId);
        if (aead == null) {
            throw new CryptoException("Unknown key id '" + keyId + "'");
        }
        return aead;
    }

    /** Round-trips a probe value through every derived key; throws if any key is unusable. */
    final void selfCheck() throws GeneralSecurityException {
        byte[] probe = "patientvault-self-check".getBytes(StandardCharsets.UTF_8);
        byte[] aad = new byte[] {1};
        for (Map<String, Aead> ring : java.util.List.of(keks, userSecretKeys)) {
            for (Aead aead : ring.values()) {
                if (!Arrays.equals(probe, aead.decrypt(aead.encrypt(probe, aad), aad))) {
                    throw new GeneralSecurityException("self-check failed");
                }
            }
        }
        blindIndexMac.computeMac(probe);
    }

    /** Safe to call at runtime (readiness probe). */
    boolean isHealthy() {
        try {
            selfCheck();
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "KeyRing[active=" + activeKeyId + ", keys=" + keks.keySet() + "]";
    }
}
