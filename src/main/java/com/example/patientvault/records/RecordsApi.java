package com.example.patientvault.records;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Public facade of the records module. */
public interface RecordsApi {

    /** Lists one folder level (null = root). Throws if the patient is not active or the folder is not theirs. */
    FolderListing browse(UUID patientId, UUID folderId);

    /** Number of available files per patient, for result cards. Patients without files are absent from the map. */
    Map<UUID, Long> countFiles(Collection<UUID> patientIds);
}
