package com.example.patientvault.records.internal;

import java.io.IOException;
import java.io.InputStream;

/** An incoming file, independent of the web framework. */
public interface UploadSource {

    /** The name sent by the browser (untrusted; sanitised before use). */
    String originalName();

    /** Size in bytes as reported by the container. */
    long size();

    /** Opens a fresh stream over the content; may be called several times. */
    InputStream open() throws IOException;
}
