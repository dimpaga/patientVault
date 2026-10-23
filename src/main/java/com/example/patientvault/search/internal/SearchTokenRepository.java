package com.example.patientvault.search.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchTokenRepository extends JpaRepository<SearchToken, SearchTokenId> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SearchToken t where t.id.patientId = :patientId")
    void deleteAllForPatient(@Param("patientId") UUID patientId);

    @Query("select t.id.patientId from SearchToken t where t.id.kind = :kind and t.id.token = :token")
    List<UUID> findPatientIds(@Param("kind") String kind, @Param("token") byte[] token, Pageable limit);

    /** Patients that have all of the given tokens (one grouped query for multi-word name searches). */
    @Query("""
            select t.id.patientId from SearchToken t
            where t.id.kind = :kind and t.id.token in :tokens
            group by t.id.patientId
            having count(distinct t.id.token) = :required
            """)
    List<UUID> findPatientIdsHavingAll(@Param("kind") String kind, @Param("tokens") List<byte[]> tokens,
                                       @Param("required") long required, Pageable limit);
}
