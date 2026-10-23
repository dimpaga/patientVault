package com.example.patientvault.records.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalFileRepository extends JpaRepository<MedicalFile, UUID> {

    Optional<MedicalFile> findByIdAndPatientId(UUID id, UUID patientId);

    /** Files of a folder level (folderId null = root) in the given statuses. */
    @Query("select f from MedicalFile f where f.patientId = :patientId and f.status in :statuses and "
            + "((:folderId is null and f.folderId is null) or f.folderId = :folderId)")
    List<MedicalFile> findInFolder(@Param("patientId") UUID patientId, @Param("folderId") UUID folderId,
                                   @Param("statuses") Collection<String> statuses);

    List<MedicalFile> findByPatientIdAndStatus(UUID patientId, String status);

    List<MedicalFile> findByPatientIdAndFolderIdIn(UUID patientId, Collection<UUID> folderIds);

    @Query("select f.patientId, count(f) from MedicalFile f where f.status = 'AVAILABLE' and f.patientId in :ids group by f.patientId")
    List<Object[]> countAvailableByPatient(@Param("ids") Collection<UUID> ids);

    @Query("select f.folderId, count(f) from MedicalFile f where f.patientId = :patientId and f.status = 'AVAILABLE' "
            + "and f.folderId is not null group by f.folderId")
    List<Object[]> countAvailableByFolder(@Param("patientId") UUID patientId);
}
