package com.example.patientvault.patient.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A selectable reason for a visit (read-only lookup, maintained in the database). */
@Entity
@Table(name = "doctor_visit_reasons")
public class VisitReasonEntity {

    @Id
    private String code;

    @Column(name = "name_el", nullable = false)
    private String nameEl;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected VisitReasonEntity() {
    }

    public String getCode() {
        return code;
    }

    public String getNameEl() {
        return nameEl;
    }

    public String getNameEn() {
        return nameEn;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
