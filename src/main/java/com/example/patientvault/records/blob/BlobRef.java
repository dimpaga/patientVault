package com.example.patientvault.records.blob;

import java.util.UUID;

/**
 * Handle to a stored blob: the object keys of the encrypted content and of the thumbnail ({@code null} when there is
 * none). {@code ciphertextSize} is only filled in by {@link BlobStore#put}.
 */
public record BlobRef(String key, String thumbnailKey, long ciphertextSize) {

    /** Both keys of a file as derived from its ids, for operations that must also cover a thumbnail that may exist. */
    public static BlobRef of(UUID patientId, UUID fileId) {
        return new BlobRef(StorageKeys.content(patientId, fileId), StorageKeys.thumbnail(patientId, fileId), 0);
    }
}
