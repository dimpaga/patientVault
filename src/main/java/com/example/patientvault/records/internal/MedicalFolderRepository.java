package com.example.patientvault.records.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalFolderRepository extends JpaRepository<MedicalFolder, UUID> {

    List<MedicalFolder> findByPatientId(UUID patientId);

    Optional<MedicalFolder> findByIdAndPatientId(UUID id, UUID patientId);
}
