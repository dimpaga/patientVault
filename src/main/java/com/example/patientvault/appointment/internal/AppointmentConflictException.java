package com.example.patientvault.appointment.internal;

/** Someone (or another tab) changed the appointment after the form was loaded. */
public class AppointmentConflictException extends RuntimeException {

    public AppointmentConflictException() {
        super("Appointment was modified concurrently");
    }
}
