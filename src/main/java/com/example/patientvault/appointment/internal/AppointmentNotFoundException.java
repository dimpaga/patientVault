package com.example.patientvault.appointment.internal;

/** An appointment, patient or reminder that does not exist or does not belong together. */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException() {
        super("Not found");
    }
}
