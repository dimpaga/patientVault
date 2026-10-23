package com.example.patientvault.appointment.internal;

import java.time.Instant;
import java.util.UUID;

import com.example.patientvault.shared.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Appointment row. The reason is ciphertext (encrypted with the patient's data key). */
@Entity
@Table(name = "appointment")
public class Appointment {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private String status = SCHEDULED;

    @Column(name = "reason_enc")
    private byte[] reasonEnc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Appointment() {
    }

    Appointment(UUID patientId) {
        this.id = Ids.newId();
        this.patientId = patientId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public byte[] getReasonEnc() {
        return reasonEnc;
    }

    public void setReasonEnc(byte[] reasonEnc) {
        this.reasonEnc = reasonEnc;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Appointment[id=" + id + "]";
    }
}
