package com.example.patientvault.appointment;

import java.time.LocalTime;
import java.util.UUID;

/** An appointment as the dashboard shows it. {@code toString()} prints ids only. */
public record AppointmentSummary(UUID id, UUID patientId, String patientName, LocalTime time, int durationMinutes) {

    @Override
    public String toString() {
        return "AppointmentSummary[id=" + id + "]";
    }
}
