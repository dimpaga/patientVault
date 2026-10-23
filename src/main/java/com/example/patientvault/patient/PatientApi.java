package com.example.patientvault.patient;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public facade of the patient module. Other modules use this instead of the entities or repositories. */
public interface PatientApi {

    /** True when the patient exists and is ACTIVE (not being erased). */
    boolean existsActive(UUID patientId);

    Optional<PatientSummary> findActive(UUID patientId);

    /** ACTIVE patients among the given ids, in one query. Unknown or erasing patients are absent. */
    Map<UUID, PatientSummary> findActive(Collection<UUID> patientIds);

    /** Most recently touched patients (patient edited or visit added/changed), newest first. */
    List<PatientSummary> recent(int limit);

    /** Records a visit (for example from a completed appointment). The date must not be in the future. */
    UUID addVisit(UUID patientId, LocalDate date, String description);
}
