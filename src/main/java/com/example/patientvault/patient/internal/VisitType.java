package com.example.patientvault.patient.internal;

import java.util.Optional;

/** Kind of visit. The name is what is stored in {@code visit.visit_type}; the readable text is the message key below. */
public enum VisitType {
    DOCTOR_VISIT,
    SURGERY;

    public String messageKey() {
        return "visit.type." + name();
    }

    /** Blank means the default (a doctor visit); an unknown value is empty. */
    public static Optional<VisitType> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(DOCTOR_VISIT);
        }
        for (VisitType type : values()) {
            if (type.name().equals(value.strip())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
