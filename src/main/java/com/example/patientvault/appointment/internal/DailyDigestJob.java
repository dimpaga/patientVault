package com.example.patientvault.appointment.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * Optional morning agenda e-mail ({@code app.reminders.daily-digest.enabled}, at {@code at}). The day is recorded in
 * {@code digest_run} so a restart after the send time never sends it twice. A day without appointments sends nothing.
 * A failing send is retried every minute, at most {@value #MAX_TRIES_PER_DAY} times a day.
 */
@Component
class DailyDigestJob {

    private static final Logger LOG = LoggerFactory.getLogger(DailyDigestJob.class);
    static final int MAX_TRIES_PER_DAY = 5;

    private final DigestRunRepository runs;
    private final AppointmentRepository appointments;
    private final PatientApi patients;
    private final DoctorContact contact;
    private final MailComposer composer;
    private final MailSender mail;
    private final AuditService audit;
    private final ReminderProperties properties;
    private final Clock clock;
    private final ZoneId zone;
    private final TransactionTemplate tx;
    private final AtomicReference<LocalDate> triesDay = new AtomicReference<>();
    private final AtomicInteger tries = new AtomicInteger();

    DailyDigestJob(DigestRunRepository runs, AppointmentRepository appointments, PatientApi patients,
                   DoctorContact contact, MailComposer composer, MailSender mail, AuditService audit,
                   ReminderProperties properties, Clock clock, @Value("${app.timezone}") ZoneId zone,
                   PlatformTransactionManager transactionManager) {
        this.runs = runs;
        this.appointments = appointments;
        this.patients = patients;
        this.contact = contact;
        this.composer = composer;
        this.mail = mail;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.zone = zone;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 25_000)
    void scheduled() {
        try {
            runIfDue();
        } catch (RuntimeException e) {
            LOG.error("Daily digest failed: {}", e.getClass().getSimpleName());
        }
    }

    /** @return true when a mail was sent in this call */
    boolean runIfDue() {
        if (!properties.dailyDigest().enabled()) {
            return false;
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalTime now = LocalTime.now(clock.withZone(zone));
        if (now.isBefore(properties.dailyDigest().at()) || runs.existsById(today)) {
            return false;
        }
        if (!today.equals(triesDay.get())) {
            triesDay.set(today);
            tries.set(0);
        }
        if (tries.incrementAndGet() > MAX_TRIES_PER_DAY) {
            return false;
        }
        List<MailComposer.DigestItem> items = agenda(today);
        if (items.isEmpty()) {
            markDone(today);
            return false;
        }
        Optional<String> recipient = contact.notificationEmail();
        if (recipient.isEmpty()) {
            audit.record(AuditEvent.failure(AuditAction.JOB_RUN).byActor(SystemAuthentication.ACTOR)
                    .withDetail("job", "DailyDigestJob").withDetail("error", "NoRecipient"));
            return false;
        }
        try {
            mail.send(composer.digest(recipient.get(), today, items));
        } catch (MailSendException e) {
            audit.record(AuditEvent.failure(AuditAction.JOB_RUN).byActor(SystemAuthentication.ACTOR)
                    .withDetail("job", "DailyDigestJob").withDetail("error", e.errorClass())
                    .withDetail("attempt", tries.get()));
            return false;
        }
        markDone(today);
        audit.record(AuditEvent.success(AuditAction.JOB_RUN).byActor(SystemAuthentication.ACTOR)
                .withDetail("job", "DailyDigestJob").withDetail("appointments", items.size()));
        return true;
    }

    private List<MailComposer.DigestItem> agenda(LocalDate day) {
        return tx.execute(status -> {
            List<Appointment> todays = appointments.findInRange(day.atStartOfDay(zone).toInstant(),
                    day.plusDays(1).atStartOfDay(zone).toInstant(), List.of(Appointment.SCHEDULED));
            Map<UUID, PatientSummary> names = SystemAuthentication.run(() -> patients.findActive(
                    todays.stream().map(Appointment::getPatientId).distinct().toList()), Permissions.PATIENT_READ);
            return todays.stream().filter(a -> names.containsKey(a.getPatientId()))
                    .map(a -> new MailComposer.DigestItem(a.getScheduledAt().atZone(zone).toLocalTime(), names.get(a.getPatientId())))
                    .toList();
        });
    }

    private void markDone(LocalDate day) {
        tx.executeWithoutResult(status -> runs.save(new DigestRun(day, clock.instant())));
    }
}
