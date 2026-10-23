package com.example.patientvault.appointment.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByIdAndPatientId(UUID id, UUID patientId);

    List<Appointment> findByPatientIdOrderByScheduledAtDesc(UUID patientId);

    /** Appointments in a half-open time range, earliest first, in the given statuses. */
    @Query("select a from Appointment a where a.scheduledAt >= :from and a.scheduledAt < :to and a.status in :statuses "
            + "order by a.scheduledAt, a.id")
    List<Appointment> findInRange(@Param("from") Instant from, @Param("to") Instant to,
                                  @Param("statuses") Collection<String> statuses);
}
