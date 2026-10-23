package com.example.patientvault.shared;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Counts the bytes of the response body without buffering it, so large file downloads stay streamed.
 * Both the stream and the writer delegate straight to the container, which keeps flush/reset semantics.
 */
final class CountingResponse extends HttpServletResponseWrapper {

    private long bytes;
    private ServletOutputStream stream;
    private PrintWriter writer;

    CountingResponse(HttpServletResponse response) {
        super(response);
    }

    long bytesWritten() {
        return bytes;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (stream == null) {
            stream = new CountingStream(super.getOutputStream());
        }
        return stream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new CountingWriter(super.getWriter()));
        }
        return writer;
    }

    @Override
    public void reset() {
        bytes = 0;
        super.reset();
    }

    @Override
    public void resetBuffer() {
        bytes = 0;
        super.resetBuffer();
    }

    private final class CountingStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        CountingStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            bytes++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            bytes += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            delegate.setWriteListener(listener);
        }
    }

    /** Counts UTF-8 bytes of the characters written (the application always renders UTF-8). */
    private final class CountingWriter extends Writer {

        private final Writer delegate;

        CountingWriter(Writer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(char[] buffer, int off, int len) throws IOException {
            delegate.write(buffer, off, len);
            for (int i = off; i < off + len; i++) {
                char c = buffer[i];
                bytes += c < 0x80 ? 1 : c < 0x800 ? 2 : Character.isSurrogate(c) ? 2 : 3;
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
