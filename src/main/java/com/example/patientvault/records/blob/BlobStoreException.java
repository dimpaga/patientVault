package com.example.patientvault.records.blob;

/** The object store could not complete an operation (unreachable, rejected or inconsistent). Messages carry no file content or names. */
public class BlobStoreException extends RuntimeException {

    public BlobStoreException(String message) {
        super(message);
    }

    public BlobStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
