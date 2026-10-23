package com.example.patientvault.appointment.internal;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DigestRunRepository extends JpaRepository<DigestRun, LocalDate> {
}
