package com.example.patientvault.records.blob;

import java.time.Instant;

/** Non-personal metadata written into the CouchDB document next to the encrypted attachment. */
public record BlobMeta(String keyId, String cipher, long plaintextSize, Instant createdAt) {
}
