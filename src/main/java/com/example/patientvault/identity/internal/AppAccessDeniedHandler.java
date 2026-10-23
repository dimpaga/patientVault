package com.example.patientvault.identity.internal;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Access denied and CSRF failures. Both are audited. A CSRF failure that was caused by an expired
 * session sends the user to the login page; everything else answers 403 (rendered by the error page).
 */
class AppAccessDeniedHandler implements AccessDeniedHandler {

    private final AuditService audit;

    AppAccessDeniedHandler(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        boolean csrf = ex instanceof CsrfException;
        audit.record(AuditEvent.denied(csrf ? AuditAction.CSRF_FAILURE : AuditAction.ACCESS_DENIED)
                .withDetail("method", request.getMethod()));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName());
        if (csrf && (anonymous || LoginRedirects.sessionExpired(request))) {
            LoginRedirects.toLogin(request, response, true);
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
