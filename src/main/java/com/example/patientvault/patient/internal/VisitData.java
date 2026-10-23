package com.example.patientvault.patient.internal;

import java.time.LocalDate;

/** Validated visit values (plaintext, memory only). Empty optional texts are {@code null}. */
public record VisitData(LocalDate visitDate, String description, String diagnosis, String treatment) {

    @Override
    public String toString() {
        return "VisitData[date=" + visitDate + "]";
    }
}
