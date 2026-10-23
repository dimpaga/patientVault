package com.example.patientvault.patient.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.patientvault.crypto.CryptoService;
import com.example.patientvault.crypto.DataKey;
import com.example.patientvault.records.PatientAccess;

/** Gives the records module what it needs from patients without exposing entities or repositories. */
@Component
class PatientAccessAdapter implements PatientAccess {

    private final PatientRepository patients;
    private final CryptoService crypto;

    PatientAccessAdapter(PatientRepository patients, CryptoService crypto) {
        this.patients = patients;
        this.crypto = crypto;
    }

    @Override
    public boolean isActive(UUID patientId) {
        return patients.findByIdAndStatus(patientId, PatientEntity.ACTIVE).isPresent();
    }

    @Override
    public DataKey dataKey(UUID patientId) {
        PatientEntity patient = patients.findByIdAndStatus(patientId, PatientEntity.ACTIVE)
                .orElseThrow(PatientNotFoundException::new);
        return crypto.unwrapDataKey(patient.getId(), patient.getDekWrapped());
    }
}
