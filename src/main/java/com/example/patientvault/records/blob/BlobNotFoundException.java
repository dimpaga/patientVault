package com.example.patientvault.records.blob;

/** The requested document or attachment does not exist in the object store. */
public class BlobNotFoundException extends BlobStoreException {

    public BlobNotFoundException() {
        super("Blob not found");
    }
}
