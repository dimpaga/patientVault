package com.example.patientvault.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public facade of the patient module. Other modules use this instead of the entities or repositories. */
public interface PatientApi {

    /** True when the patient exists and is ACTIVE (not being erased). */
    boolean existsActive(UUID patientId);

    Optional<PatientSummary> findActive(UUID patientId);

    /** Most recently touched patients (patient edited or visit added/changed), newest first. */
    List<PatientSummary> recent(int limit);
}
