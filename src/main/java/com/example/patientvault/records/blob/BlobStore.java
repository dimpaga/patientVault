package com.example.patientvault.records.blob;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Object storage for encrypted file content (spec 5.3). The rest of the application never talks to CouchDB
 * directly. Everything stored through it is already ciphertext.
 */
public interface BlobStore {

    /** Creates the document and attaches the encrypted content; on failure nothing is left behind. */
    BlobRef put(UUID fileId, UUID patientId, InputStream encryptedContent, long ciphertextLength, BlobMeta meta);

    void putThumbnail(BlobRef ref, InputStream encryptedThumb, long length);

    /** Streaming read; the caller closes the stream. */
    InputStream openContent(BlobRef ref);

    Optional<InputStream> openThumbnail(BlobRef ref);

    /** Deletes the document and purges every revision (idempotent: a missing document is fine). */
    void erase(BlobRef ref);

    /** Asks CouchDB to reclaim space left by deleted and purged revisions. */
    void compact();

    /** Ids of all documents in the file database, for the nightly reconciliation job. */
    Set<String> listAllDocumentIds();
}
