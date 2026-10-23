package com.example.patientvault.audit.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

/**
 * Interim implementation that writes audit events to the application log. Replaced in step 3 by the
 * append-only database-backed service, which also resolves the actor from the security context.
 */
@Service
class LoggingAuditService implements AuditService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");

    @Override
    public void record(AuditEvent event) {
        AUDIT.info("action={} outcome={} actor={} entityType={} entityId={} details={}",
                event.action(), event.outcome(), event.actor() != null ? event.actor() : "UNRESOLVED",
                event.entityType(), event.entityId(), event.details());
    }
}
