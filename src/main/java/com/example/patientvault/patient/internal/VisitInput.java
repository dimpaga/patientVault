package com.example.patientvault.patient.internal;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/** Raw visit form values. {@code toString()} never prints the values. */
public record VisitInput(
        LocalDate visitDate,
        @Size(max = 20) String visitType,
        @Size(max = PatientValidator.MAX_REASON_CODE) String reason,
        @Size(max = PatientValidator.MAX_TEXT) String diagnosis,
        @Size(max = PatientValidator.MAX_TEXT) String treatment) {

    @Override
    public String toString() {
        return "VisitInput[...]";
    }
}
