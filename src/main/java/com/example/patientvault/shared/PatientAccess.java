package com.example.patientvault.shared;

import java.util.UUID;

import com.example.patientvault.crypto.DataKey;

/**
 * What the records module needs from the patient module, expressed as a port so that {@code records} does not
 * depend on {@code patient}: whether a patient may be worked on, and the patient's data key for encrypting
 * file names and content. Implemented by the patient module.
 */
public interface PatientAccess {

    /** True when the patient exists and is ACTIVE (not being erased). */
    boolean isActive(UUID patientId);

    /** The unwrapped data key of an ACTIVE patient; throws if the patient is unknown or not active. */
    DataKey dataKey(UUID patientId);
}
