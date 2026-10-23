package com.example.patientvault.audit;

/** Records audit events. Called from services so that non-web entry points (jobs) are covered too. */
public interface AuditService {

    void record(AuditEvent event);
}
