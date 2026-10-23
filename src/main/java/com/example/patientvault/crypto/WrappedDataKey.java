package com.example.patientvault.crypto;

import java.util.Arrays;

/** A data-encryption key wrapped by a key-encryption key; this is what gets stored in the database. */
public record WrappedDataKey(byte[] bytes, String keyId) {

    public WrappedDataKey {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WrappedDataKey other && keyId.equals(other.keyId) && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * keyId.hashCode() + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "WrappedDataKey[keyId=" + keyId + "]";
    }
}
