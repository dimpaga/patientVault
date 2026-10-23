package com.example.patientvault.search.internal;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SearchTokenId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "token", nullable = false)
    private byte[] token;

    protected SearchTokenId() {
    }

    public SearchTokenId(UUID patientId, String kind, byte[] token) {
        this.patientId = patientId;
        this.kind = kind;
        this.token = token;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public String getKind() {
        return kind;
    }

    public byte[] getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SearchTokenId other && Objects.equals(patientId, other.patientId)
                && Objects.equals(kind, other.kind) && Arrays.equals(token, other.token);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(patientId, kind) + Arrays.hashCode(token);
    }

    @Override
    public String toString() {
        return "SearchTokenId[patient=" + patientId + ", kind=" + kind + "]";
    }
}
