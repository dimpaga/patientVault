package com.example.patientvault.crypto.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

/**
 * Pull-style encryption: reads plaintext in small chunks, pushes it through an encrypting
 * {@link OutputStream} that writes into an in-memory buffer, and hands the produced ciphertext to the
 * caller. Memory use is bounded by one cipher segment plus one chunk.
 */
final class EncryptingInputStream extends InputStream {

    private static final int CHUNK = 64 * 1024;

    private static final class Buffer extends ByteArrayOutputStream {
        byte[] drain() {
            byte[] out = toByteArray();
            reset();
            return out;
        }
    }

    interface EncryptingSinkFactory {
        OutputStream open(OutputStream sink) throws IOException, GeneralSecurityException;
    }

    private final InputStream source;
    private final Buffer sink = new Buffer();
    private final OutputStream encrypting;
    private final byte[] chunk = new byte[CHUNK];
    private byte[] current;
    private int position;
    private boolean finished;
    private boolean closed;

    EncryptingInputStream(InputStream source, EncryptingSinkFactory factory)
            throws IOException, GeneralSecurityException {
        this.source = source;
        this.encrypting = factory.open(sink);
        this.current = sink.drain();
    }

    private boolean fill() throws IOException {
        while (position >= current.length) {
            if (finished) {
                return false;
            }
            int read = source.read(chunk);
            if (read < 0) {
                encrypting.close();
                finished = true;
            } else if (read > 0) {
                encrypting.write(chunk, 0, read);
            }
            current = sink.drain();
            position = 0;
        }
        return true;
    }

    @Override
    public int read() throws IOException {
        if (!fill()) {
            return -1;
        }
        return current[position++] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (!fill()) {
            return -1;
        }
        int count = Math.min(length, current.length - position);
        System.arraycopy(current, position, target, offset, count);
        position += count;
        return count;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            source.close();
        } finally {
            try {
                encrypting.close();
            } catch (IOException ignored) {
                // the stream is being abandoned; any final segment would be discarded anyway
            }
        }
    }
}
