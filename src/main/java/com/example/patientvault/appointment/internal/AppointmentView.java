package com.example.patientvault.appointment.internal;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An appointment ready for display. {@code scheduled} is local time in the configured zone;
 * {@code pending/sent/failed} count the reminders of the appointment. {@code toString()} prints ids only.
 */
public record AppointmentView(UUID id, UUID patientId, String patientName, LocalDateTime scheduled,
                              int durationMinutes, String status, String reason, long version,
                              int pendingReminders, int sentReminders, int failedReminders) {

    public LocalDateTime end() {
        return scheduled.plusMinutes(durationMinutes);
    }

    public boolean scheduledStatus() {
        return Appointment.SCHEDULED.equals(status);
    }

    @Override
    public String toString() {
        return "AppointmentView[id=" + id + "]";
    }
}
