package com.example.patientvault.search;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the search module: maintains the blind-index tokens of a patient and answers exact
 * lookups. Callers pass plain values; only HMAC tokens are persisted.
 */
public interface SearchIndex {

    /** Replaces all tokens of the patient. Joins the caller's transaction. */
    void index(UUID patientId, IndexedFields fields);

    /** Patient owning this AMKA (spaces and dashes are ignored), if any. */
    Optional<UUID> findByAmka(String amka);

    List<UUID> findByEmail(String email);

    List<UUID> findByMobile(String mobileE164);
}
