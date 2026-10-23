package com.example.patientvault.records.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Folder row; the name is ciphertext. */
@Entity
@Table(name = "medical_folder")
public class MedicalFolder {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name_enc", nullable = false)
    private byte[] nameEnc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MedicalFolder() {
    }

    MedicalFolder(UUID id, UUID patientId, UUID parentId, byte[] nameEnc) {
        this.id = id;
        this.patientId = patientId;
        this.parentId = parentId;
        this.nameEnc = nameEnc;
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

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public byte[] getNameEnc() {
        return nameEnc;
    }

    public void setNameEnc(byte[] nameEnc) {
        this.nameEnc = nameEnc;
    }

    @Override
    public String toString() {
        return "MedicalFolder[id=" + id + "]";
    }
}
