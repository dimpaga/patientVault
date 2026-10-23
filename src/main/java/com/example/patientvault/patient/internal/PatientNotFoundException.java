package com.example.patientvault.patient.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The patient (or visit) does not exist, is being erased, or does not belong to the given patient. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException() {
        super("Not found");
    }
}
