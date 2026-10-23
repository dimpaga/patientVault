package com.example.patientvault.patient.internal;

import java.time.Instant;
import java.util.UUID;

/** A patient as shown to the doctor: decrypted data plus technical fields. */
public record PatientView(
        UUID id,
        long version,
        PatientData data,
        Instant consentRecordedAt,
        Instant createdAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "PatientView[id=" + id + "]";
    }
}
