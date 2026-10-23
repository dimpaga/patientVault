package com.example.patientvault.appointment.internal;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Marks a local day whose agenda e-mail has been handled. */
@Entity
@Table(name = "digest_run")
public class DigestRun {

    @Id
    private LocalDate day;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected DigestRun() {
    }

    DigestRun(LocalDate day, Instant sentAt) {
        this.day = day;
        this.sentAt = sentAt;
    }

    public LocalDate getDay() {
        return day;
    }
}
