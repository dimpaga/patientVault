package com.example.patientvault.patient.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PatientRepository extends JpaRepository<PatientEntity, UUID> {

    Optional<PatientEntity> findByIdAndStatus(UUID id, String status);

    /** Most recently touched first: the later of the patient's own update and its newest visit change. */
    @Query("""
            select p from PatientEntity p
            where p.status = 'ACTIVE'
            order by greatest(p.updatedAt,
                              coalesce((select max(v.updatedAt) from VisitEntity v where v.patientId = p.id), p.updatedAt)) desc,
                     p.id
            """)
    List<PatientEntity> findRecent(Pageable pageable);
}
