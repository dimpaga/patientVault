package com.example.patientvault.patient.internal;

/** The patient (or visit) does not exist, is being erased, or does not belong to the given patient. */
public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException() {
        super("Not found");
    }
}
