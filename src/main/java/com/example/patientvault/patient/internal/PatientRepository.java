package com.example.patientvault.patient.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** One page of all active patients, most recently touched first (the empty search). */
    @Query(value = """
            select p from PatientEntity p
            where p.status = 'ACTIVE'
            order by greatest(p.updatedAt,
                              coalesce((select max(v.updatedAt) from VisitEntity v where v.patientId = p.id), p.updatedAt)) desc,
                     p.id
            """, countQuery = "select count(p) from PatientEntity p where p.status = 'ACTIVE'")
    Page<PatientEntity> findRecentPage(Pageable pageable);

    /** One page of the active patients among the given candidates, most recently touched first. */
    @Query(value = """
            select p from PatientEntity p
            where p.id in :ids and p.status = 'ACTIVE'
            order by greatest(p.updatedAt,
                              coalesce((select max(v.updatedAt) from VisitEntity v where v.patientId = p.id), p.updatedAt)) desc,
                     p.id
            """, countQuery = "select count(p) from PatientEntity p where p.id in :ids and p.status = 'ACTIVE'")
    Page<PatientEntity> findActiveByIds(@Param("ids") Collection<UUID> ids, Pageable pageable);
}
