package com.example.patientvault.patient.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.patient.PatientApi;
import com.example.patientvault.patient.PatientSummary;
import com.example.patientvault.shared.Permissions;

@Service
class DefaultPatientApi implements PatientApi {

    private final PatientRepository patients;
    private final PatientCodec codec;
    private final PatientService service;

    DefaultPatientApi(PatientRepository patients, PatientCodec codec, PatientService service) {
        this.patients = patients;
        this.codec = codec;
        this.service = service;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActive(UUID patientId) {
        return patients.findByIdAndStatus(patientId, PatientEntity.ACTIVE).isPresent();
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public Optional<PatientSummary> findActive(UUID patientId) {
        return patients.findByIdAndStatus(patientId, PatientEntity.ACTIVE).map(this::summary);
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public Map<UUID, PatientSummary> findActive(Collection<UUID> patientIds) {
        if (patientIds.isEmpty()) {
            return Map.of();
        }
        return patients.findAllById(patientIds).stream().filter(p -> PatientEntity.ACTIVE.equals(p.getStatus()))
                .collect(Collectors.toMap(PatientEntity::getId, this::summary));
    }

    @Override
    public UUID addVisit(UUID patientId, LocalDate date, String description) {
        return service.addVisit(patientId, new VisitData(date, description, null, null));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public List<PatientSummary> recent(int limit) {
        return patients.findRecent(PageRequest.of(0, limit)).stream().map(this::summary).toList();
    }

    private PatientSummary summary(PatientEntity entity) {
        String[] name = codec.decodeName(entity);
        return new PatientSummary(entity.getId(), name[0], name[1]);
    }
}
