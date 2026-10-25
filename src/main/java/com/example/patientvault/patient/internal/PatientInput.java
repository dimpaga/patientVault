package com.example.patientvault.patient.internal;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/**
 * Raw form values for creating or editing a patient, exactly as submitted. The visit fields are only used
 * when creating a patient (the first visit). {@code toString()} never prints the values.
 */
public record PatientInput(
        @Size(max = PatientValidator.MAX_NAME) String firstName,
        @Size(max = PatientValidator.MAX_NAME) String lastName,
        @Size(max = PatientValidator.MAX_EMAIL) String email,
        @Size(max = PatientValidator.MAX_PHONE) String mobile,
        @Size(max = PatientValidator.MAX_AMKA_INPUT) String amka,
        @Size(max = PatientValidator.MAX_REFERRAL) String referralSource,
        @Size(max = PatientValidator.MAX_TEXT) String notes,
        boolean consentGiven,
        LocalDate visitDate,
        @Size(max = 20) String visitType,
        @Size(max = PatientValidator.MAX_REASON_CODE) String visitReason,
        @Size(max = PatientValidator.MAX_TEXT) String visitDiagnosis,
        @Size(max = PatientValidator.MAX_TEXT) String visitTreatment) {

    @Override
    public String toString() {
        return "PatientInput[...]";
    }
}
