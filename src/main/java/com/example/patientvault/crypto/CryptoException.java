package com.example.patientvault.crypto;

/** Unchecked failure of a cryptographic operation. Messages never contain key or plaintext material. */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
