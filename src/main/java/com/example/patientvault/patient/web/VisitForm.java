package com.example.patientvault.patient.web;

import java.time.LocalDate;

import com.example.patientvault.patient.internal.VisitData;
import com.example.patientvault.patient.internal.VisitInput;
import com.example.patientvault.patient.internal.VisitType;

/** Add/edit visit form. {@code version} (edit only) drives the concurrent-edit check. {@code toString()} prints nothing. */
record VisitForm(Long version, LocalDate visitDate, String visitType, String reason, String diagnosis, String treatment) {

    static VisitForm empty(LocalDate today) {
        return new VisitForm(null, today, VisitType.DOCTOR_VISIT.name(), "", "", "");
    }

    static VisitForm from(VisitData data, long version) {
        return new VisitForm(version, data.visitDate(), data.type().name(), nullToEmpty(data.reason()),
                nullToEmpty(data.diagnosis()), nullToEmpty(data.treatment()));
    }

    VisitInput toInput() {
        return new VisitInput(visitDate, visitType, reason, diagnosis, treatment);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "VisitForm[...]";
    }
}
