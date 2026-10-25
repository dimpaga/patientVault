package com.example.patientvault.patient.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.patientvault.shared.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Visit row (date in the clear, medical text as ciphertext). */
@Entity
@Table(name = "visit")
public class VisitEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "visit_type", nullable = false)
    private String visitType = "DOCTOR_VISIT";

    @Column(name = "reason_enc")
    private byte[] reasonEnc;

    @Column(name = "diagnosis_enc")
    private byte[] diagnosisEnc;

    @Column(name = "treatment_enc")
    private byte[] treatmentEnc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected VisitEntity() {
    }

    VisitEntity(UUID patientId) {
        this.id = Ids.newId();
        this.patientId = patientId;
    }

    /** Id is needed before persisting because visit ciphertext is bound to it. */
    VisitEntity(UUID id, UUID patientId) {
        this.id = id;
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

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(LocalDate visitDate) {
        this.visitDate = visitDate;
    }

    public String getVisitType() {
        return visitType;
    }

    public void setVisitType(String visitType) {
        this.visitType = visitType;
    }

    public byte[] getReasonEnc() {
        return reasonEnc;
    }

    public void setReasonEnc(byte[] reasonEnc) {
        this.reasonEnc = reasonEnc;
    }

    public byte[] getDiagnosisEnc() {
        return diagnosisEnc;
    }

    public void setDiagnosisEnc(byte[] diagnosisEnc) {
        this.diagnosisEnc = diagnosisEnc;
    }

    public byte[] getTreatmentEnc() {
        return treatmentEnc;
    }

    public void setTreatmentEnc(byte[] treatmentEnc) {
        this.treatmentEnc = treatmentEnc;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "VisitEntity[id=" + id + "]";
    }
}
