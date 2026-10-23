package com.example.patientvault.patient.internal;

import java.util.UUID;

/** Another patient already has this AMKA. {@code existingPatientId} may be null when found via the unique index race. */
public class DuplicateAmkaException extends RuntimeException {

    private final transient UUID existingPatientId;

    public DuplicateAmkaException(UUID existingPatientId) {
        super("AMKA already registered");
        this.existingPatientId = existingPatientId;
    }

    public UUID getExistingPatientId() {
        return existingPatientId;
    }
}
