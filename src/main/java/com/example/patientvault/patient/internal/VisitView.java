package com.example.patientvault.patient.internal;

import java.time.Instant;
import java.util.UUID;

public record VisitView(UUID id, long version, VisitData data, Instant updatedAt) {

    @Override
    public String toString() {
        return "VisitView[id=" + id + "]";
    }
}
