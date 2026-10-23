package com.example.patientvault.audit.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

/**
 * Interim implementation that writes audit events to the application log. Replaced in step 3 by the
 * append-only database-backed service.
 */
@Service
class LoggingAuditService implements AuditService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");

    @Override
    public void record(AuditEvent event) {
        AUDIT.info("action={} outcome={} actor={} entityType={} entityId={} details={}",
                event.action(), event.outcome(), actor(event), event.entityType(), event.entityId(), event.details());
    }

    private static String actor(AuditEvent event) {
        if (event.actor() != null) {
            return event.actor();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())
                ? auth.getName() : "ANONYMOUS";
    }
}
