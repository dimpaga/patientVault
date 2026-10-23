package com.example.patientvault.patient.internal;

/** The record was changed by someone else (or another tab) after the form was loaded. */
public class ConcurrentEditException extends RuntimeException {

    public ConcurrentEditException() {
        super("Record was modified concurrently");
    }
}
