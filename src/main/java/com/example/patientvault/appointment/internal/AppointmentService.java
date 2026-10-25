package com.example.patientvault.appointment.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.appointment.AppointmentApi;
import com.example.patientvault.appointment.AppointmentSummary;
import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;
import com.example.patientvault.crypto.CryptoService;
import com.example.patientvault.crypto.DataKey;
import com.example.patientvault.patient.PatientApi;
import com.example.patientvault.patient.PatientSummary;
import com.example.patientvault.shared.PatientAccess;
import com.example.patientvault.shared.Permissions;

/**
 * Appointments of a patient (spec 8.6). Times are stored in UTC and shown/entered in {@code app.timezone}.
 * Creating or rescheduling regenerates the reminders from the configured lead times, cancelling cancels the
 * pending ones. The reason is encrypted with the patient's data key; audit entries carry ids only.
 */
@Service
public class AppointmentService implements AppointmentApi {

    public static final int MIN_DURATION = 5;
    public static final int MAX_DURATION = 480;
    public static final int MAX_REASON = 500;
    private static final int FAILURE_ALERT_DAYS = 7;

    private final AppointmentRepository appointments;
    private final ReminderRepository reminders;
    private final PatientApi patients;
    private final PatientAccess access;
    private final CryptoService crypto;
    private final AuditService audit;
    private final ReminderProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    AppointmentService(AppointmentRepository appointments, ReminderRepository reminders, PatientApi patients,
                       PatientAccess access, CryptoService crypto, AuditService audit, ReminderProperties properties,
                       Clock clock, @Value("${app.timezone}") ZoneId zone) {
        ReminderPlanner.validate(properties.leadTimes());
        this.appointments = appointments;
        this.reminders = reminders;
        this.patients = patients;
        this.access = access;
        this.crypto = crypto;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    // ---- commands -----------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public UUID create(UUID patientId, LocalDateTime scheduledLocal, int durationMinutes, String reason) {
        requireActive(patientId);
        Instant at = validate(scheduledLocal, durationMinutes, reason);
        DataKey key = access.dataKey(patientId);
        Appointment appointment = new Appointment(patientId);
        appointment.setScheduledAt(at);
        appointment.setDurationMinutes(durationMinutes);
        appointment.setReasonEnc(encryptReason(key, appointment.getId(), reason));
        appointments.saveAndFlush(appointment);
        int planned = regenerateReminders(appointment);
        audit.record(AuditEvent.success(AuditAction.APPOINTMENT_CREATED).onEntity("APPOINTMENT", appointment.getId())
                .withDetail("patientId", patientId).withDetail("reminders", planned));
        return appointment.getId();
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public void reschedule(UUID patientId, UUID appointmentId, long expectedVersion, LocalDateTime scheduledLocal,
                           int durationMinutes, String reason) {
        requireActive(patientId);
        Appointment appointment = scheduled(patientId, appointmentId);
        if (appointment.getVersion() != expectedVersion) {
            throw new AppointmentConflictException();
        }
        Instant at = validate(scheduledLocal, durationMinutes, reason);
        DataKey key = access.dataKey(patientId);
        boolean timeChanged = !at.equals(appointment.getScheduledAt());
        appointment.setScheduledAt(at);
        appointment.setDurationMinutes(durationMinutes);
        appointment.setReasonEnc(encryptReason(key, appointmentId, reason));
        try {
            appointments.saveAndFlush(appointment);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new AppointmentConflictException();
        }
        int planned = timeChanged ? regenerateReminders(appointment) : -1;
        AuditEvent event = AuditEvent.success(AuditAction.APPOINTMENT_UPDATED).onEntity("APPOINTMENT", appointmentId)
                .withDetail("patientId", patientId).withDetail("rescheduled", timeChanged);
        audit.record(planned >= 0 ? event.withDetail("reminders", planned) : event);
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public void cancel(UUID patientId, UUID appointmentId) {
        requireActive(patientId);
        Appointment appointment = scheduled(patientId, appointmentId);
        appointment.setStatus(Appointment.CANCELLED);
        reminders.findByAppointmentIdOrderByRemindAt(appointmentId).stream()
                .filter(r -> Reminder.PENDING.equals(r.getStatus())).forEach(r -> r.setStatus(Reminder.CANCELLED));
        audit.record(AuditEvent.success(AuditAction.APPOINTMENT_CANCELLED).onEntity("APPOINTMENT", appointmentId)
                .withDetail("patientId", patientId));
    }

    /** Marks the appointment completed and records a visit for it (dated like the appointment, never in the future). */
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public UUID complete(UUID patientId, UUID appointmentId) {
        requireActive(patientId);
        Appointment appointment = scheduled(patientId, appointmentId);
        LocalDate visitDate = appointment.getScheduledAt().atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        UUID visitId = patients.addVisit(patientId, visitDate.isAfter(today) ? today : visitDate);
        appointment.setStatus(Appointment.COMPLETED);
        reminders.findByAppointmentIdOrderByRemindAt(appointmentId).stream()
                .filter(r -> Reminder.PENDING.equals(r.getStatus())).forEach(r -> r.setStatus(Reminder.CANCELLED));
        audit.record(AuditEvent.success(AuditAction.APPOINTMENT_UPDATED).onEntity("APPOINTMENT", appointmentId)
                .withDetail("patientId", patientId).withDetail("completed", true).withDetail("visitId", visitId));
        return visitId;
    }

    // ---- queries ------------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public AppointmentView get(UUID patientId, UUID appointmentId) {
        requireActive(patientId);
        Appointment appointment = appointments.findByIdAndPatientId(appointmentId, patientId)
                .orElseThrow(AppointmentNotFoundException::new);
        return views(List.of(appointment)).get(0);
    }

    /** Patient of an appointment, for the link in the reminder e-mail. */
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public UUID patientIdOf(UUID appointmentId) {
        Appointment appointment = appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
        requireActive(appointment.getPatientId());
        return appointment.getPatientId();
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public List<AppointmentView> forPatient(UUID patientId) {
        requireActive(patientId);
        return views(appointments.findByPatientIdOrderByScheduledAtDesc(patientId));
    }

    /** SCHEDULED appointments whose local date is in {@code [from, to)} (earliest first). Includes ones already past. */
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public List<AppointmentView> scheduledBetween(LocalDate from, LocalDate to) {
        return views(appointments.findInRange(from.atStartOfDay(zone).toInstant(), to.atStartOfDay(zone).toInstant(),
                List.of(Appointment.SCHEDULED)));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public List<AppointmentSummary> today() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return scheduledBetween(today, today.plusDays(1)).stream()
                .map(v -> new AppointmentSummary(v.id(), v.patientId(), v.patientName(), v.scheduled().toLocalTime(),
                        v.durationMinutes())).toList();
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public long recentFailedReminders() {
        return reminders.countByStatusAndRemindAtAfter(Reminder.FAILED,
                clock.instant().minus(FAILURE_ALERT_DAYS, ChronoUnit.DAYS));
    }

    // ---- internals ----------------------------------------------------------------------------

    /** Checks the form and converts the local date-time to an instant. Gap times (spring forward) move forward. */
    private Instant validate(LocalDateTime scheduledLocal, int durationMinutes, String reason) {
        Map<String, String> errors = new LinkedHashMap<>();
        Instant at = null;
        if (scheduledLocal == null) {
            errors.put("scheduledAt", "appointment.error.required");
        } else {
            at = ZonedDateTime.ofLocal(scheduledLocal, zone, null).toInstant();
            if (!at.isAfter(clock.instant())) {
                errors.put("scheduledAt", "appointment.error.past");
            }
        }
        if (durationMinutes < MIN_DURATION || durationMinutes > MAX_DURATION) {
            errors.put("durationMinutes", "appointment.error.duration");
        }
        if (reason != null && reason.strip().length() > MAX_REASON) {
            errors.put("reason", "appointment.error.reason.tooLong");
        }
        if (!errors.isEmpty()) {
            throw new AppointmentValidationException(errors);
        }
        return at;
    }

    /** Replaces the PENDING reminders by freshly planned ones; returns how many were planned. */
    private int regenerateReminders(Appointment appointment) {
        reminders.deletePending(appointment.getId());
        List<Instant> plan = ReminderPlanner.plan(appointment.getScheduledAt(), zone, properties.leadTimes(), clock.instant());
        Set<Instant> existing = new java.util.HashSet<>();
        reminders.findByAppointmentIdOrderByRemindAt(appointment.getId()).forEach(r -> existing.add(r.getRemindAt()));
        int planned = 0;
        for (Instant at : plan) {
            if (existing.add(at)) {
                reminders.save(new Reminder(appointment.getId(), at));
                planned++;
            }
        }
        return planned;
    }

    private Appointment scheduled(UUID patientId, UUID appointmentId) {
        Appointment appointment = appointments.findByIdAndPatientId(appointmentId, patientId)
                .orElseThrow(AppointmentNotFoundException::new);
        if (!Appointment.SCHEDULED.equals(appointment.getStatus())) {
            throw new AppointmentNotFoundException();
        }
        return appointment;
    }

    private void requireActive(UUID patientId) {
        if (!access.isActive(patientId)) {
            throw new AppointmentNotFoundException();
        }
    }

    private List<AppointmentView> views(Collection<Appointment> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = source.stream().map(Appointment::getId).toList();
        Map<UUID, int[]> counts = new HashMap<>();
        for (Reminder r : reminders.findByAppointmentIdIn(ids)) {
            int[] c = counts.computeIfAbsent(r.getAppointmentId(), k -> new int[3]);
            switch (r.getStatus()) {
                case Reminder.PENDING -> c[0]++;
                case Reminder.SENT -> c[1]++;
                case Reminder.FAILED -> c[2]++;
                default -> { }
            }
        }
        Map<UUID, PatientSummary> names = patients.findActive(source.stream().map(Appointment::getPatientId).distinct().toList());
        Map<UUID, DataKey> keys = new HashMap<>();
        List<AppointmentView> views = new ArrayList<>(source.size());
        for (Appointment a : source) {
            PatientSummary patient = names.get(a.getPatientId());
            if (patient == null) {
                continue; // patient is being erased or gone
            }
            DataKey key = keys.computeIfAbsent(a.getPatientId(), access::dataKey);
            int[] c = counts.getOrDefault(a.getId(), new int[3]);
            views.add(new AppointmentView(a.getId(), a.getPatientId(), patient.fullName(),
                    LocalDateTime.ofInstant(a.getScheduledAt(), zone), a.getDurationMinutes(), a.getStatus(),
                    decryptReason(key, a), a.getVersion(), c[0], c[1], c[2]));
        }
        return views;
    }

    private byte[] encryptReason(DataKey key, UUID appointmentId, String reason) {
        String text = reason == null ? "" : reason.strip();
        return text.isEmpty() ? null : crypto.encryptText(key, reasonField(appointmentId), text);
    }

    private String decryptReason(DataKey key, Appointment appointment) {
        return appointment.getReasonEnc() == null ? null
                : crypto.decryptText(key, reasonField(appointment.getId()), appointment.getReasonEnc());
    }

    private static String reasonField(UUID appointmentId) {
        return "appointment_reason:" + appointmentId;
    }
}
