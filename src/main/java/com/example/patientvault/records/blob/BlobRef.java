package com.example.patientvault.records.blob;

import java.util.UUID;

/**
 * Handle to a stored blob. {@code rev} is the CouchDB revision that was current when the handle was
 * returned; operations that need the latest revision look it up themselves.
 */
public record BlobRef(String docId, String rev) {

    public static BlobRef of(UUID fileId) {
        return new BlobRef(fileId.toString(), null);
    }
}
