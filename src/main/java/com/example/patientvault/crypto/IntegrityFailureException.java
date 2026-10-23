package com.example.patientvault.crypto;

/** Raised when stored ciphertext fails authentication: it was tampered with, moved or is corrupt. */
public class IntegrityFailureException extends CryptoException {

    public IntegrityFailureException(String message) {
        super(message);
    }

    public IntegrityFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
