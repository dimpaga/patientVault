package com.example.patientvault.records.internal;

/** Stored ciphertext failed authentication (tampered, corrupt or unreadable key). Carries no file details. */
public class FileIntegrityException extends RuntimeException {

    public FileIntegrityException(Throwable cause) {
        super("File integrity check failed", cause);
    }
}
