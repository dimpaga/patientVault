package com.example.patientvault.patient.internal;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/** Raw visit form values. {@code toString()} never prints the values. */
public record VisitInput(
        LocalDate visitDate,
        @Size(max = PatientValidator.MAX_TEXT) String description,
        @Size(max = PatientValidator.MAX_TEXT) String diagnosis,
        @Size(max = PatientValidator.MAX_TEXT) String treatment) {

    @Override
    public String toString() {
        return "VisitInput[...]";
    }
}
