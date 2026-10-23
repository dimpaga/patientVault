package com.example.patientvault.crypto.internal;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.patientvault.crypto.CryptoException;
import com.example.patientvault.crypto.KeyProvider;

/**
 * Reads the key ring from {@code APP_MASTER_KEYS} / {@code APP_ACTIVE_KEY_ID} and refuses to start
 * when it is missing, malformed or contains a key shorter than 256 bits. Error messages mention key
 * ids and positions only, never key material, and never chain the original parser exception.
 */
@Component
class EnvKeyProvider implements KeyProvider {

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,16}");
    private static final int MIN_KEY_BYTES = 32;

    private final Map<String, byte[]> keys;
    private final String activeKeyId;
    private final String blindIndexKeyId;

    EnvKeyProvider(CryptoProperties properties) {
        this.keys = parse(properties.getMasterKeys());
        this.activeKeyId = requireKnown(properties.getActiveKeyId(), "APP_ACTIVE_KEY_ID");
        String blind = properties.getBlindIndexKeyId();
        this.blindIndexKeyId = (blind == null || blind.isBlank())
                ? keys.keySet().iterator().next()
                : requireKnown(blind, "APP_BLIND_INDEX_KEY_ID");
    }

    private static Map<String, byte[]> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("APP_MASTER_KEYS is not set");
        }
        Map<String, byte[]> parsed = new LinkedHashMap<>();
        String[] entries = raw.split(",");
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i].trim();
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                throw new IllegalStateException(
                        "APP_MASTER_KEYS entry #" + (i + 1) + " is malformed, expected <id>:<base64 key>");
            }
            String id = entry.substring(0, colon);
            if (!KEY_ID.matcher(id).matches()) {
                throw new IllegalStateException(
                        "APP_MASTER_KEYS entry #" + (i + 1) + " has an invalid key id (allowed: A-Z a-z 0-9 _ -, max 16)");
            }
            if (parsed.containsKey(id)) {
                throw new IllegalStateException("APP_MASTER_KEYS contains duplicate key id '" + id + "'");
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(entry.substring(colon + 1).trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("APP_MASTER_KEYS key '" + id + "' is not valid base64");
            }
            if (key.length < MIN_KEY_BYTES) {
                throw new IllegalStateException("APP_MASTER_KEYS key '" + id + "' is shorter than 256 bits");
            }
            parsed.put(id, key);
        }
        return Collections.unmodifiableMap(parsed);
    }

    private String requireKnown(String keyId, String variable) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException(variable + " is not set");
        }
        if (!keys.containsKey(keyId.trim())) {
            throw new IllegalStateException(variable + " refers to unknown key id '" + keyId.trim() + "'");
        }
        return keyId.trim();
    }

    @Override
    public String activeKeyId() {
        return activeKeyId;
    }

    @Override
    public String blindIndexKeyId() {
        return blindIndexKeyId;
    }

    @Override
    public Set<String> keyIds() {
        return keys.keySet();
    }

    @Override
    public byte[] masterKey(String keyId) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw new CryptoException("Unknown key id '" + keyId + "'");
        }
        return key.clone();
    }

    @Override
    public String toString() {
        return "EnvKeyProvider[active=" + activeKeyId + ", keys=" + keys.keySet() + "]";
    }
}
