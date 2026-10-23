package com.example.patientvault.patient;

import java.util.UUID;

/** Minimal patient identity for other modules and pages. {@code toString()} prints the id only. */
public record PatientSummary(UUID id, String firstName, String lastName) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        return "PatientSummary[id=" + id + "]";
    }
}
