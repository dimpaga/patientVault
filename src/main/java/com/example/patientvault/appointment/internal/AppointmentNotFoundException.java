package com.example.patientvault.appointment.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** An appointment, patient or reminder that does not exist or does not belong together. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException() {
        super("Not found");
    }
}
