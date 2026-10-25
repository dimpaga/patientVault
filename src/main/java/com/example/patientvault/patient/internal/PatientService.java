package com.example.patientvault.patient.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;
import com.example.patientvault.shared.Permissions;
import com.example.patientvault.search.IndexedFields;
import com.example.patientvault.search.SearchIndex;
import com.example.patientvault.shared.PhoneNumbers;
import com.example.patientvault.shared.Ids;

/**
 * Patient use cases. Every method is authorised by permission, works on ACTIVE patients only, encrypts
 * through {@link PatientCodec}, keeps the search tokens in step in the same transaction and audits what
 * happened (ids and changed field names only).
 */
@Service
public class PatientService {

    /** @param warnings message keys of non-blocking warnings (for example a repeated e-mail address) */
    public record SaveResult(UUID id, long version, List<String> warnings) {
    }

    private final PatientRepository patients;
    private final VisitRepository visits;
    private final PatientCodec codec;
    private final SearchIndex search;
    private final AuditService audit;
    private final Clock clock;

    PatientService(PatientRepository patients, VisitRepository visits, PatientCodec codec, SearchIndex search,
                   AuditService audit, Clock clock) {
        this.patients = patients;
        this.visits = visits;
        this.codec = codec;
        this.search = search;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- patients -----------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public SaveResult create(PatientData data, VisitData firstVisit) {
        search.findByAmka(data.amka()).ifPresent(existing -> {
            throw new DuplicateAmkaException(existing);
        });
        List<String> warnings = duplicateWarnings(data, null);

        UUID id = Ids.newId();
        PatientEntity entity = codec.newEntity(id, data);
        if (data.consentGiven()) {
            entity.setConsentRecordedAt(clock.instant());
        }
        patients.saveAndFlush(entity);
        indexOrFail(entity.getId(), data);
        audit.record(AuditEvent.success(AuditAction.PATIENT_CREATED).onEntity("PATIENT", id));

        if (firstVisit != null) {
            saveVisit(entity, firstVisit);
        }
        return new SaveResult(id, entity.getVersion(), warnings);
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional
    public Optional<PatientView> get(UUID id) {
        Optional<PatientView> view = patients.findByIdAndStatus(id, PatientEntity.ACTIVE).map(this::toView);
        view.ifPresent(v -> audit.record(AuditEvent.success(AuditAction.PATIENT_VIEWED).onEntity("PATIENT", id)));
        return view;
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public SaveResult update(UUID id, long expectedVersion, PatientData data) {
        PatientEntity entity = patients.findByIdAndStatus(id, PatientEntity.ACTIVE)
                .orElseThrow(PatientNotFoundException::new);
        if (entity.getVersion() != expectedVersion) {
            throw new ConcurrentEditException();
        }
        PatientData before = codec.decode(entity);
        List<String> changed = changedFields(before, data);
        if (changed.isEmpty()) {
            return new SaveResult(id, entity.getVersion(), List.of());
        }

        boolean amkaChanged = !before.amka().equals(data.amka());
        if (amkaChanged) {
            search.findByAmka(data.amka()).filter(existing -> !existing.equals(id)).ifPresent(existing -> {
                throw new DuplicateAmkaException(existing);
            });
        }
        List<String> warnings = duplicateWarnings(data, id);

        codec.update(entity, data);
        if (data.consentGiven() && !before.consentGiven()) {
            entity.setConsentRecordedAt(clock.instant());
        } else if (!data.consentGiven()) {
            entity.setConsentRecordedAt(null);
        }
        try {
            patients.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentEditException();
        }
        if (searchableChanged(before, data)) {
            indexOrFail(id, data);
        }
        audit.record(AuditEvent.success(AuditAction.PATIENT_UPDATED).onEntity("PATIENT", id)
                .withDetail("fields", String.join(",", changed)));
        return new SaveResult(id, entity.getVersion(), warnings);
    }

    /** What a uniqueness lookup found for an inline-validated field. */
    public record Uniqueness(FieldMessage error, FieldMessage warning, UUID existingPatientId) {
        static final Uniqueness NONE = new Uniqueness(null, null, null);
    }

    /**
     * Inline check while typing: a taken AMKA is an error (with the owner for a link), a repeated e-mail or
     * mobile is only a warning. {@code self} is the patient being edited, if any.
     */
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional(readOnly = true)
    public Uniqueness checkUniqueness(String field, String rawValue, UUID self) {
        return switch (field) {
            case "amka" -> search.findByAmka(Amka.normalize(rawValue)).filter(other -> !other.equals(self))
                    .map(other -> new Uniqueness(FieldMessage.of("patient.error.amka.duplicate"), null, other))
                    .orElse(Uniqueness.NONE);
            case "email" -> search.findByEmail(rawValue).stream().anyMatch(other -> !other.equals(self))
                    ? new Uniqueness(null, FieldMessage.of("patient.warning.email.duplicate"), null) : Uniqueness.NONE;
            case "mobile" -> PhoneNumbers.toE164(rawValue)
                    .filter(e164 -> search.findByMobile(e164).stream().anyMatch(other -> !other.equals(self)))
                    .map(e164 -> new Uniqueness(null, FieldMessage.of("patient.warning.mobile.duplicate"), null))
                    .orElse(Uniqueness.NONE);
            default -> Uniqueness.NONE;
        };
    }

    // ---- visits -------------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public List<VisitView> visits(UUID patientId) {
        PatientEntity patient = active(patientId);
        return visits.findByPatientIdOrderByVisitDateDescCreatedAtDesc(patientId).stream()
                .map(v -> new VisitView(v.getId(), v.getVersion(), codec.decode(v, patient), v.getUpdatedAt()))
                .toList();
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public UUID addVisit(UUID patientId, VisitData data) {
        return saveVisit(active(patientId), data);
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public void updateVisit(UUID patientId, UUID visitId, long expectedVersion, VisitData data) {
        PatientEntity patient = active(patientId);
        VisitEntity visit = visits.findByIdAndPatientId(visitId, patientId).orElseThrow(PatientNotFoundException::new);
        if (visit.getVersion() != expectedVersion) {
            throw new ConcurrentEditException();
        }
        VisitData before = codec.decode(visit, patient);
        List<String> changed = changedFields(before, data);
        if (changed.isEmpty()) {
            return;
        }
        codec.update(visit, patient, data);
        try {
            visits.saveAndFlush(visit);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentEditException();
        }
        audit.record(AuditEvent.success(AuditAction.VISIT_UPDATED).onEntity("VISIT", visitId)
                .withDetail("patientId", patientId).withDetail("fields", String.join(",", changed)));
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    @Transactional
    public void deleteVisit(UUID patientId, UUID visitId) {
        active(patientId);
        VisitEntity visit = visits.findByIdAndPatientId(visitId, patientId).orElseThrow(PatientNotFoundException::new);
        visits.delete(visit);
        audit.record(AuditEvent.success(AuditAction.VISIT_DELETED).onEntity("VISIT", visitId)
                .withDetail("patientId", patientId));
    }

    // ---- internals ----------------------------------------------------------------------------

    private UUID saveVisit(PatientEntity patient, VisitData data) {
        VisitEntity visit = codec.newVisit(patient, data);
        visits.save(visit);
        audit.record(AuditEvent.success(AuditAction.VISIT_CREATED).onEntity("VISIT", visit.getId())
                .withDetail("patientId", patient.getId()));
        return visit.getId();
    }

    private PatientEntity active(UUID patientId) {
        return patients.findByIdAndStatus(patientId, PatientEntity.ACTIVE).orElseThrow(PatientNotFoundException::new);
    }

    private PatientView toView(PatientEntity entity) {
        return new PatientView(entity.getId(), entity.getVersion(), codec.decode(entity),
                entity.getConsentRecordedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    /** The unique AMKA index is the last line of defence against two requests creating the same AMKA at once. */
    private void indexOrFail(UUID id, PatientData data) {
        try {
            search.index(id, new IndexedFields(data.firstName(), data.lastName(), data.email(), data.mobile(), data.amka()));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateAmkaException(null);
        }
    }

    private List<String> duplicateWarnings(PatientData data, UUID self) {
        List<String> warnings = new ArrayList<>();
        if (search.findByEmail(data.email()).stream().anyMatch(other -> !other.equals(self))) {
            warnings.add("patient.warning.email.duplicate");
        }
        if (search.findByMobile(data.mobile()).stream().anyMatch(other -> !other.equals(self))) {
            warnings.add("patient.warning.mobile.duplicate");
        }
        return warnings;
    }

    private static boolean searchableChanged(PatientData before, PatientData after) {
        return !Objects.equals(before.firstName(), after.firstName()) || !Objects.equals(before.lastName(), after.lastName())
                || !Objects.equals(before.email(), after.email()) || !Objects.equals(before.mobile(), after.mobile())
                || !Objects.equals(before.amka(), after.amka());
    }

    /** Names (not values) of the fields that differ. */
    private static List<String> changedFields(PatientData a, PatientData b) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(a.firstName(), b.firstName())) {
            changed.add("firstName");
        }
        if (!Objects.equals(a.lastName(), b.lastName())) {
            changed.add("lastName");
        }
        if (!Objects.equals(a.email(), b.email())) {
            changed.add("email");
        }
        if (!Objects.equals(a.mobile(), b.mobile())) {
            changed.add("mobile");
        }
        if (!Objects.equals(a.amka(), b.amka())) {
            changed.add("amka");
        }
        if (!Objects.equals(a.referralSource(), b.referralSource())) {
            changed.add("referralSource");
        }
        if (!Objects.equals(a.notes(), b.notes())) {
            changed.add("notes");
        }
        if (a.consentGiven() != b.consentGiven()) {
            changed.add("consent");
        }
        return changed;
    }

    private static List<String> changedFields(VisitData a, VisitData b) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(a.visitDate(), b.visitDate())) {
            changed.add("visitDate");
        }
        if (!Objects.equals(a.reason(), b.reason())) {
            changed.add("reason");
        }
        if (!Objects.equals(a.diagnosis(), b.diagnosis())) {
            changed.add("diagnosis");
        }
        if (!Objects.equals(a.treatment(), b.treatment())) {
            changed.add("treatment");
        }
        return changed;
    }
}
