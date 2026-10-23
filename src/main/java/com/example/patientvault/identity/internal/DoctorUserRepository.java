package com.example.patientvault.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DoctorUserRepository extends JpaRepository<DoctorUser, UUID> {

    Optional<DoctorUser> findByUsername(String username);

    /** Serialises concurrent failed attempts so parallel guessing cannot dodge the failure counter. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from DoctorUser u where u.username = :username")
    Optional<DoctorUser> findByUsernameForUpdate(@Param("username") String username);

    @Modifying
    @Query("update DoctorUser u set u.lockedUntil = null, u.failedAttempts = 0 "
            + "where u.lockedUntil is not null and u.lockedUntil < :cutoff")
    int resetLockoutsExpiredBefore(@Param("cutoff") Instant cutoff);
}
