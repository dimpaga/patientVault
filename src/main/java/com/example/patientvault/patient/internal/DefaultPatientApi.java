package com.example.patientvault.patient.internal;

import java.util.List;
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

    DefaultPatientApi(PatientRepository patients, PatientCodec codec) {
        this.patients = patients;
        this.codec = codec;
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
    public List<PatientSummary> recent(int limit) {
        return patients.findRecent(PageRequest.of(0, limit)).stream().map(this::summary).toList();
    }

    private PatientSummary summary(PatientEntity entity) {
        String[] name = codec.decodeName(entity);
        return new PatientSummary(entity.getId(), name[0], name[1]);
    }
}
