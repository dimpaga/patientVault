package com.example.patientvault.patient.web;

import java.time.LocalDate;

import com.example.patientvault.patient.internal.PatientData;
import com.example.patientvault.patient.internal.PatientInput;

/**
 * The create/edit patient form (explicit binding object, never an entity). {@code version} is the record
 * version the form was loaded from and drives the concurrent-edit check. {@code toString()} prints nothing.
 */
record PatientForm(
        Long version,
        String firstName,
        String lastName,
        String email,
        String mobile,
        String amka,
        String referralSource,
        String notes,
        Boolean consentGiven,
        LocalDate visitDate,
        String visitDescription,
        String visitDiagnosis,
        String visitTreatment) {

    static PatientForm empty() {
        return new PatientForm(null, "", "", "", "", "", "", "", false, null, "", "", "");
    }

    static PatientForm from(PatientData data, long version) {
        return new PatientForm(version, data.firstName(), data.lastName(), data.email(), data.mobile(), data.amka(),
                nullToEmpty(data.referralSource()), nullToEmpty(data.notes()), data.consentGiven(),
                null, "", "", "");
    }

    PatientInput toInput() {
        return new PatientInput(firstName, lastName, email, mobile, amka, referralSource, notes, Boolean.TRUE.equals(consentGiven),
                visitDate, visitDescription, visitDiagnosis, visitTreatment);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "PatientForm[...]";
    }
}
