package com.example.patientvault.patient.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<VisitEntity, UUID> {

    List<VisitEntity> findByPatientIdOrderByVisitDateDescCreatedAtDesc(UUID patientId);

    Optional<VisitEntity> findByIdAndPatientId(UUID id, UUID patientId);

    long countByPatientId(UUID patientId);
}
