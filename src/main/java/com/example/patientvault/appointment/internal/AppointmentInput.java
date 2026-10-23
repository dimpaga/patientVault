package com.example.patientvault.appointment.internal;

import java.time.LocalDateTime;
import java.util.UUID;

/** Raw appointment form. {@code toString()} prints nothing. */
public record AppointmentInput(UUID patientId, Long version, LocalDateTime scheduledAt, Integer durationMinutes,
                               String reason) {

    @Override
    public String toString() {
        return "AppointmentInput[...]";
    }
}
