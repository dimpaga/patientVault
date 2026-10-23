package com.example.patientvault.patient.internal;

/**
 * A validation message: a message-bundle key plus one optional argument (for example a maximum length).
 * Never contains the offending value, so it is safe to log.
 */
public record FieldMessage(String key, Object arg) {

    public static FieldMessage of(String key) {
        return new FieldMessage(key, null);
    }
}
