package com.example.patientvault.patient.internal;

/**
 * Validated, normalised patient values (plaintext). Only lives in memory: created by the validator or by
 * {@link PatientCodec}. {@code toString()} never prints the values.
 *
 * @param email lower-case address
 * @param mobile E.164 number
 * @param amka the 11 digits without separators
 */
public record PatientData(
        String firstName,
        String lastName,
        String email,
        String mobile,
        String amka,
        String referralSource,
        String notes,
        boolean consentGiven) {

    @Override
    public String toString() {
        return "PatientData[...]";
    }
}
