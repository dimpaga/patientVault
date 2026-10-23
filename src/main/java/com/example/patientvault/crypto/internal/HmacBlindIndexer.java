package com.example.patientvault.crypto.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import org.springframework.stereotype.Component;

import com.example.patientvault.crypto.BlindIndexer;
import com.example.patientvault.crypto.CryptoException;

@Component
class HmacBlindIndexer implements BlindIndexer {

    private final KeyRing keyRing;

    HmacBlindIndexer(KeyRing keyRing) {
        this.keyRing = keyRing;
    }

    @Override
    public byte[] token(String kind, String normalizedValue) {
        byte[] input = (kind + ":" + normalizedValue).getBytes(StandardCharsets.UTF_8);
        try {
            return keyRing.blindIndexMac().computeMac(input);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Blind index computation failed", e);
        }
    }
}
