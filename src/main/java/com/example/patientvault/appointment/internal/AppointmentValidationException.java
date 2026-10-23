package com.example.patientvault.appointment.internal;

import java.util.Map;

/** The form is not valid; {@code errors} maps a field name to a message-bundle key. */
public class AppointmentValidationException extends RuntimeException {

    private final transient Map<String, String> errors;

    public AppointmentValidationException(Map<String, String> errors) {
        super("Invalid appointment");
        this.errors = errors;
    }

    public Map<String, String> errors() {
        return errors;
    }
}
