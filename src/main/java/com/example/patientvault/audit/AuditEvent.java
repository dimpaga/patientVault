package com.example.patientvault.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One audit record. Only identifiers and non-personal context may be put in it: entity ids (UUIDs),
 * changed field names, counts. Never names, emails, phone numbers, AMKA, notes, file names or the
 * text a user typed into a login or search box.
 *
 * @param actor optional explicit actor ({@code SYSTEM}, {@code ANONYMOUS}); when {@code null} the
 *              audit service resolves it from the current security context
 */
public record AuditEvent(
        AuditAction action,
        AuditOutcome outcome,
        String actor,
        String entityType,
        String entityId,
        Map<String, String> details) {

    public AuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AuditEvent success(AuditAction action) {
        return new AuditEvent(action, AuditOutcome.SUCCESS, null, null, null, Map.of());
    }

    public static AuditEvent failure(AuditAction action) {
        return new AuditEvent(action, AuditOutcome.FAILURE, null, null, null, Map.of());
    }

    public static AuditEvent denied(AuditAction action) {
        return new AuditEvent(action, AuditOutcome.DENIED, null, null, null, Map.of());
    }

    public AuditEvent byActor(String newActor) {
        return new AuditEvent(action, outcome, newActor, entityType, entityId, details);
    }

    public AuditEvent onEntity(String type, Object id) {
        return new AuditEvent(action, outcome, actor, type, String.valueOf(id), details);
    }

    public AuditEvent withDetail(String key, Object value) {
        Map<String, String> merged = new LinkedHashMap<>(details);
        merged.put(key, String.valueOf(value));
        return new AuditEvent(action, outcome, actor, entityType, entityId, merged);
    }
}
