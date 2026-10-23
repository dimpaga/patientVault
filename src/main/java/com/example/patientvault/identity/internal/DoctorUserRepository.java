package com.example.patientvault.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorUserRepository extends JpaRepository<DoctorUser, UUID> {

    Optional<DoctorUser> findByUsername(String username);
}
