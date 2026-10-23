package com.example.patientvault.appointment.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.example.patientvault.appointment.internal.AppointmentView;

/**
 * Add/edit appointment form. The raw strings are kept so that an invalid value is shown again exactly as typed.
 * {@code version} (edit only) drives the concurrent-edit check. {@code toString()} prints nothing.
 */
record AppointmentForm(Long version, String scheduledAt, String durationMinutes, String reason) {

    static final int DEFAULT_DURATION = 30;
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    static AppointmentForm empty() {
        return new AppointmentForm(null, "", String.valueOf(DEFAULT_DURATION), "");
    }

    static AppointmentForm from(AppointmentView view) {
        return new AppointmentForm(view.version(), view.scheduled().format(INPUT),
                String.valueOf(view.durationMinutes()), view.reason() == null ? "" : view.reason());
    }

    /** Null when empty or not a valid date-time; the service then reports "required". */
    LocalDateTime parsedScheduledAt() {
        if (scheduledAt == null || scheduledAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(scheduledAt.strip());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** -1 when not a number; the service then reports an invalid duration. */
    int parsedDuration() {
        try {
            return Integer.parseInt(durationMinutes == null ? "" : durationMinutes.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return "AppointmentForm[...]";
    }
}
