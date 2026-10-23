package com.example.patientvault.appointment.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    /**
     * Due PENDING reminders, locked with {@code FOR UPDATE SKIP LOCKED}: a second scheduler run (or instance)
     * skips rows that are being processed, so no reminder is handled twice. Must run inside a transaction.
     */
    @Query(value = """
            select * from reminder
            where status = 'PENDING' and remind_at <= :now and (next_attempt_at is null or next_attempt_at <= :now)
            order by remind_at
            limit :batch
            for update skip locked
            """, nativeQuery = true)
    List<Reminder> lockDue(@Param("now") Instant now, @Param("batch") int batch);

    List<Reminder> findByAppointmentIdOrderByRemindAt(UUID appointmentId);

    List<Reminder> findByAppointmentIdIn(Collection<UUID> appointmentIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Reminder r where r.appointmentId = :appointmentId and r.status = 'PENDING'")
    void deletePending(@Param("appointmentId") UUID appointmentId);

    /** FAILED reminders whose reminder time is not older than {@code since}: shown as a dashboard alert. */
    long countByStatusAndRemindAtAfter(String status, Instant since);
}
