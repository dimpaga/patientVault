package com.example.patientvault.records.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** File metadata row. The name is ciphertext; the content is in the object store under {@code couchDocId}. */
@Entity
@Table(name = "medical_file")
public class MedicalFile {

    public static final String PENDING = "PENDING";
    public static final String AVAILABLE = "AVAILABLE";
    public static final String DELETING = "DELETING";

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "visit_id")
    private UUID visitId;

    @Column(nullable = false)
    private String category;

    @Column(name = "display_name_enc", nullable = false)
    private byte[] displayNameEnc;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "ciphertext_sha256")
    private byte[] ciphertextSha256;

    @Column(name = "couch_doc_id", nullable = false, updatable = false)
    private String couchDocId;

    @Column(name = "has_thumbnail", nullable = false)
    private boolean hasThumbnail;

    @Column(nullable = false)
    private String status = PENDING;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MedicalFile() {
    }

    MedicalFile(UUID id, UUID patientId, UUID folderId, String category, byte[] displayNameEnc, String mimeType,
                long sizeBytes) {
        this.id = id;
        this.patientId = patientId;
        this.folderId = folderId;
        this.category = category;
        this.displayNameEnc = displayNameEnc;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.couchDocId = id.toString();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        uploadedAt = now;
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

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public String getCategory() {
        return category;
    }

    public byte[] getDisplayNameEnc() {
        return displayNameEnc;
    }

    public void setDisplayNameEnc(byte[] displayNameEnc) {
        this.displayNameEnc = displayNameEnc;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getCiphertextSha256() {
        return ciphertextSha256;
    }

    public void setCiphertextSha256(byte[] ciphertextSha256) {
        this.ciphertextSha256 = ciphertextSha256;
    }

    public String getCouchDocId() {
        return couchDocId;
    }

    public boolean isHasThumbnail() {
        return hasThumbnail;
    }

    public void setHasThumbnail(boolean hasThumbnail) {
        this.hasThumbnail = hasThumbnail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    @Override
    public String toString() {
        return "MedicalFile[id=" + id + "]";
    }
}
