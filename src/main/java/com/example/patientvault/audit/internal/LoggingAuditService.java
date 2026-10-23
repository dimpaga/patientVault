package com.example.patientvault.audit.internal;

import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;
import com.example.patientvault.shared.LogContext;

/**
 * Interim implementation that writes audit events to the application log; the append-only database table
 * and audit page are a later step. Security-related actions go to the {@code security} stream, business
 * actions to {@code audit}. The request id and user id come from the logging context of the request.
 */
@Service
class LoggingAuditService implements AuditService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");
    private static final Logger SECURITY = LoggerFactory.getLogger("security");

    private static final Set<AuditAction> SECURITY_ACTIONS = EnumSet.of(
            AuditAction.LOGIN_SUCCESS, AuditAction.LOGIN_FAILURE, AuditAction.MFA_SUCCESS, AuditAction.MFA_FAILURE,
            AuditAction.ACCOUNT_LOCKED, AuditAction.LOGOUT, AuditAction.PASSWORD_CHANGED, AuditAction.MFA_ENROLLED,
            AuditAction.ACCESS_DENIED, AuditAction.RATE_LIMITED, AuditAction.REAUTH_SUCCESS,
            AuditAction.REAUTH_FAILURE, AuditAction.RECOVERY_CODES_REGENERATED, AuditAction.SESSION_TIMEOUT,
            AuditAction.CSRF_FAILURE, AuditAction.FILE_INTEGRITY_FAILURE);

    @Override
    public void record(AuditEvent event) {
        Logger target = SECURITY_ACTIONS.contains(event.action()) ? SECURITY : AUDIT;
        MDC.put(LogContext.ACTION, event.action().name());
        MDC.put(LogContext.MODULE, module(event.action()));
        try {
            target.atInfo()
                    .addKeyValue("outcome", event.outcome())
                    .addKeyValue("actor", actor(event))
                    .addKeyValue("entityType", event.entityType())
                    .addKeyValue("entityId", event.entityId())
                    .addKeyValue("details", event.details())
                    .log("{} {} actor={} entity={}:{} details={}", event.action(), event.outcome(), actor(event),
                            event.entityType(), event.entityId(), event.details());
        } finally {
            MDC.remove(LogContext.ACTION);
            MDC.remove(LogContext.MODULE);
        }
    }

    private static String module(AuditAction action) {
        String name = action.name();
        if (action == AuditAction.FILE_INTEGRITY_FAILURE) {
            return "records";
        }
        if (SECURITY_ACTIONS.contains(action)) {
            return "identity";
        }
        if (name.startsWith("PATIENT_") || name.startsWith("VISIT_")) {
            return "patient";
        }
        if (name.startsWith("FILE_") || name.startsWith("FOLDER_")) {
            return "records";
        }
        if (name.startsWith("APPOINTMENT_") || name.startsWith("REMINDER_")) {
            return "appointment";
        }
        return name.startsWith("KEY_") ? "crypto" : "system";
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
