package com.example.patientvault.crypto;

/**
 * Computes blind-index tokens: {@code HMAC-SHA256(BLIND_INDEX_KEY, kind || ":" || normalisedValue)}.
 * Equal inputs give equal tokens, so the database can match them without ever seeing the value.
 * The caller is responsible for normalising the value first (see the search module).
 */
public interface BlindIndexer {

    byte[] token(String kind, String normalizedValue);
}
