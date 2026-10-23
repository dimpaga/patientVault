package com.example.patientvault.patient.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Patient row. Holds ciphertext only; plaintext exists solely in {@link PatientData}. */
@Entity
@Table(name = "patient")
public class PatientEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String DELETING = "DELETING";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String status = ACTIVE;

    @Column(name = "dek_wrapped", nullable = false)
    private byte[] dekWrapped;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "first_name_enc", nullable = false)
    private byte[] firstNameEnc;

    @Column(name = "last_name_enc", nullable = false)
    private byte[] lastNameEnc;

    @Column(name = "email_enc", nullable = false)
    private byte[] emailEnc;

    @Column(name = "mobile_enc", nullable = false)
    private byte[] mobileEnc;

    @Column(name = "amka_enc", nullable = false)
    private byte[] amkaEnc;

    @Column(name = "referral_source_enc")
    private byte[] referralSourceEnc;

    @Column(name = "notes_enc")
    private byte[] notesEnc;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "consent_recorded_at")
    private Instant consentRecordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PatientEntity() {
    }

    PatientEntity(UUID id, byte[] dekWrapped, String keyId) {
        this.id = id;
        this.dekWrapped = dekWrapped;
        this.keyId = keyId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public byte[] getDekWrapped() {
        return dekWrapped;
    }

    public void setDekWrapped(byte[] dekWrapped) {
        this.dekWrapped = dekWrapped;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public byte[] getFirstNameEnc() {
        return firstNameEnc;
    }

    public void setFirstNameEnc(byte[] firstNameEnc) {
        this.firstNameEnc = firstNameEnc;
    }

    public byte[] getLastNameEnc() {
        return lastNameEnc;
    }

    public void setLastNameEnc(byte[] lastNameEnc) {
        this.lastNameEnc = lastNameEnc;
    }

    public byte[] getEmailEnc() {
        return emailEnc;
    }

    public void setEmailEnc(byte[] emailEnc) {
        this.emailEnc = emailEnc;
    }

    public byte[] getMobileEnc() {
        return mobileEnc;
    }

    public void setMobileEnc(byte[] mobileEnc) {
        this.mobileEnc = mobileEnc;
    }

    public byte[] getAmkaEnc() {
        return amkaEnc;
    }

    public void setAmkaEnc(byte[] amkaEnc) {
        this.amkaEnc = amkaEnc;
    }

    public byte[] getReferralSourceEnc() {
        return referralSourceEnc;
    }

    public void setReferralSourceEnc(byte[] referralSourceEnc) {
        this.referralSourceEnc = referralSourceEnc;
    }

    public byte[] getNotesEnc() {
        return notesEnc;
    }

    public void setNotesEnc(byte[] notesEnc) {
        this.notesEnc = notesEnc;
    }

    public boolean isConsentGiven() {
        return consentGiven;
    }

    public void setConsentGiven(boolean consentGiven) {
        this.consentGiven = consentGiven;
    }

    public Instant getConsentRecordedAt() {
        return consentRecordedAt;
    }

    public void setConsentRecordedAt(Instant consentRecordedAt) {
        this.consentRecordedAt = consentRecordedAt;
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
        return "PatientEntity[id=" + id + "]";
    }
}
