package com.example.patientvault.records.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import com.example.patientvault.crypto.IntegrityFailureException;

/**
 * An opened, already decrypting file stream. The first cipher segment has been verified when this object is
 * created, so a tampered file fails before any response headers are written. Later segments are verified while
 * copying; a failure then is reported through {@code onIntegrityFailure} and rethrown. Close it when done.
 * {@code toString()} prints the id only.
 */
public final class FileContent implements AutoCloseable {

    private final UUID id;
    private final String name;
    private final String mimeType;
    private final String category;
    private final long size;
    private final InputStream stream;
    private final Runnable onIntegrityFailure;

    FileContent(UUID id, String name, String mimeType, String category, long size, InputStream stream,
                Runnable onIntegrityFailure) {
        this.id = id;
        this.name = name;
        this.mimeType = mimeType;
        this.category = category;
        this.size = size;
        this.stream = stream;
        this.onIntegrityFailure = onIntegrityFailure;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String mimeType() {
        return mimeType;
    }

    public String category() {
        return category;
    }

    public long size() {
        return size;
    }

    /** Copies the plaintext to {@code out}. A mid-stream authentication failure raises {@link FileIntegrityException}. */
    public void transferTo(OutputStream out) throws IOException {
        try {
            stream.transferTo(out);
        } catch (IntegrityFailureException e) {
            onIntegrityFailure.run();
            throw new FileIntegrityException(e);
        }
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @Override
    public String toString() {
        return "FileContent[id=" + id + "]";
    }
}
