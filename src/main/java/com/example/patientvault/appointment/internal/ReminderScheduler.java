package com.example.patientvault.appointment.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;
import com.example.patientvault.patient.PatientApi;
import com.example.patientvault.patient.PatientSummary;
import com.example.patientvault.shared.DoctorContact;
import com.example.patientvault.shared.Permissions;
import com.example.patientvault.shared.SystemAuthentication;

/**
 * Sends due reminders every minute (spec 8.6). Each reminder is handled in its own transaction that holds a row lock
 * ({@code FOR UPDATE SKIP LOCKED}), so two runs or instances never take the same reminder. Outcomes:
 * appointment gone/cancelled -> CANCELLED; appointment time passed -> EXPIRED (never a late mail for the past);
 * sent -> SENT; failed -> retried with exponential back-off, FAILED after {@code max-attempts}.
 * Reminders that became due while the application was down are still sent (late) if the appointment is in the future.
 * Delivery is at-least-once: if the process dies exactly between handing the mail to SMTP and committing SENT, that
 * one reminder is sent again after the restart.
 */
@Component
class ReminderScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderRepository reminders;
    private final AppointmentRepository appointments;
    private final PatientApi patients;
    private final DoctorContact contact;
    private final MailComposer composer;
    private final MailSender mail;
    private final AuditService audit;
    private final ReminderProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    ReminderScheduler(ReminderRepository reminders, AppointmentRepository appointments, PatientApi patients,
                      DoctorContact contact, MailComposer composer, MailSender mail, AuditService audit,
                      ReminderProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.reminders = reminders;
        this.appointments = appointments;
        this.patients = patients;
        this.contact = contact;
        this.composer = composer;
        this.mail = mail;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    void scheduled() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            LOG.error("Reminder run failed: {}", e.getClass().getSimpleName());
        }
    }

    /** Handles up to {@code batch-size} due reminders; returns how many were processed. */
    int runOnce() {
        int handled = 0;
        while (handled < properties.batchSize()) {
            Boolean more = tx.execute(status -> processNext());
            if (!Boolean.TRUE.equals(more)) {
                break;
            }
            handled++;
        }
        if (handled > 0) {
            audit.record(AuditEvent.success(AuditAction.JOB_RUN).byActor(SystemAuthentication.ACTOR)
                    .withDetail("job", "ReminderScheduler").withDetail("handled", handled));
        }
        return handled;
    }

    /** Takes one due reminder (locked) and handles it; false when none is due. */
    private boolean processNext() {
        Instant now = clock.instant();
        var due = reminders.lockDue(now, 1);
        if (due.isEmpty()) {
            return false;
        }
        Reminder reminder = due.get(0);
        Optional<Appointment> appointment = appointments.findById(reminder.getAppointmentId());
        if (appointment.isEmpty() || !Appointment.SCHEDULED.equals(appointment.get().getStatus())) {
            reminder.setStatus(Reminder.CANCELLED);
            return true;
        }
        if (!appointment.get().getScheduledAt().isAfter(now)) {
            reminder.setStatus(Reminder.EXPIRED);
            return true;
        }
        Optional<PatientSummary> patient = SystemAuthentication.run(
                () -> patients.findActive(appointment.get().getPatientId()), Permissions.PATIENT_READ);
        if (patient.isEmpty()) {
            reminder.setStatus(Reminder.CANCELLED);
            return true;
        }
        Optional<String> recipient = contact.notificationEmail();
        if (recipient.isEmpty()) {
            fail(reminder, now, "NoRecipient");
            return true;
        }
        try {
            Appointment a = appointment.get();
            mail.send(composer.reminder(recipient.get(), a.getId(), a.getScheduledAt(), a.getDurationMinutes(), patient.get()));
            reminder.setStatus(Reminder.SENT);
            reminder.setSentAt(now);
            reminder.setAttempts(reminder.getAttempts() + 1);
            reminder.setLastError(null);
            audit.record(AuditEvent.success(AuditAction.REMINDER_SENT).byActor(SystemAuthentication.ACTOR)
                    .onEntity("REMINDER", reminder.getId()).withDetail("appointmentId", a.getId())
                    .withDetail("attempt", reminder.getAttempts()));
        } catch (MailSendException e) {
            fail(reminder, now, e.errorClass());
        }
        return true;
    }

    private void fail(Reminder reminder, Instant now, String errorClass) {
        int attempts = reminder.getAttempts() + 1;
        reminder.setAttempts(attempts);
        reminder.setLastError(errorClass);
        boolean last = attempts >= properties.maxAttempts();
        if (last) {
            reminder.setStatus(Reminder.FAILED);
            reminder.setNextAttemptAt(null);
        } else {
            reminder.setNextAttemptAt(now.plus(backoff(attempts)));
        }
        audit.record(AuditEvent.failure(AuditAction.REMINDER_FAILED).byActor(SystemAuthentication.ACTOR)
                .onEntity("REMINDER", reminder.getId()).withDetail("appointmentId", reminder.getAppointmentId())
                .withDetail("attempt", attempts).withDetail("error", errorClass).withDetail("final", last));
    }

    /** base, 2*base, 4*base ... (capped at 6 hours). */
    Duration backoff(int attempts) {
        Duration delay = properties.retryBaseDelay().multipliedBy(1L << Math.min(attempts - 1, 20));
        return delay.compareTo(Duration.ofHours(6)) > 0 ? Duration.ofHours(6) : delay;
    }
}
