package com.example.patientvault.records.blob;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Object storage for encrypted file content (spec 5.3). The rest of the application never talks to the storage
 * service directly. Everything stored through it is already ciphertext.
 */
public interface BlobStore {

    /** Streams the already-encrypted content to storage; on failure nothing is left behind. Memory use is bounded by one part. */
    BlobRef put(UUID fileId, UUID patientId, InputStream encryptedContent, BlobMeta meta);

    /** Stores the encrypted thumbnail and returns its key. */
    String putThumbnail(UUID fileId, UUID patientId, byte[] encryptedThumbnail);

    /** Streaming read; the caller closes the stream (closing before the end aborts the transfer). */
    InputStream openContent(BlobRef ref);

    Optional<InputStream> openThumbnail(BlobRef ref);

    /** Deletes content and thumbnail including every object version and delete marker (idempotent: missing objects are fine). */
    void erase(BlobRef ref);
}
