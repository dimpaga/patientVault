package com.example.patientvault.identity.internal;

import java.time.Instant;
import java.util.UUID;

import com.example.patientvault.shared.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recovery_code")
public class RecoveryCode {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCode() {
    }

    public RecoveryCode(UUID userId, String codeHash) {
        this.id = Ids.newId();
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }

    @Override
    public String toString() {
        return "RecoveryCode[id=" + id + "]";
    }
}
