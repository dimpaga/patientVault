package com.example.patientvault.appointment.internal;

import java.time.Instant;
import java.util.UUID;

import com.example.patientvault.shared.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One planned e-mail for an appointment. {@code lastError} holds a technical error class only. */
@Entity
@Table(name = "reminder")
public class Reminder {

    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Column(nullable = false)
    private String status = PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Reminder() {
    }

    Reminder(UUID appointmentId, Instant remindAt) {
        this.id = Ids.newId();
        this.appointmentId = appointmentId;
        this.remindAt = remindAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    @Override
    public String toString() {
        return "Reminder[id=" + id + ", status=" + status + "]";
    }
}
