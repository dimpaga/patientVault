package com.example.patientvault.patient.internal;

import java.time.LocalDate;

/**
 * Validated visit values (plaintext, memory only). {@code reason} is the code of a row in {@code doctor_visit_reasons}.
 * {@code type} is never null. Empty optional values are {@code null}.
 */
public record VisitData(LocalDate visitDate, VisitType type, String reason, String diagnosis, String treatment) {

    @Override
    public String toString() {
        return "VisitData[date=" + visitDate + "]";
    }
}
