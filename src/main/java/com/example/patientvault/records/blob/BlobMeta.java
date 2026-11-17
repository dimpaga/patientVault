package com.example.patientvault.records.blob;

/** Technical, non-personal metadata written as user-defined object metadata next to the encrypted content. */
public record BlobMeta(String cipher) {
}
