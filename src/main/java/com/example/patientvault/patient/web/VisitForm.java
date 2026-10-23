package com.example.patientvault.patient.web;

import java.time.LocalDate;

import com.example.patientvault.patient.internal.VisitData;
import com.example.patientvault.patient.internal.VisitInput;

/** Add/edit visit form. {@code version} (edit only) drives the concurrent-edit check. {@code toString()} prints nothing. */
record VisitForm(Long version, LocalDate visitDate, String description, String diagnosis, String treatment) {

    static VisitForm empty(LocalDate today) {
        return new VisitForm(null, today, "", "", "");
    }

    static VisitForm from(VisitData data, long version) {
        return new VisitForm(version, data.visitDate(), nullToEmpty(data.description()),
                nullToEmpty(data.diagnosis()), nullToEmpty(data.treatment()));
    }

    VisitInput toInput() {
        return new VisitInput(visitDate, description, diagnosis, treatment);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "VisitForm[...]";
    }
}
