package com.example.patientvault.identity.internal;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Unauthenticated access: redirect to /login (HTMX: 401 + HX-Redirect), flagging expired sessions. */
class AppAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuditService audit;

    AppAuthenticationEntryPoint(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean expired = LoginRedirects.sessionExpired(request);
        if (expired) {
            audit.record(AuditEvent.success(AuditAction.SESSION_TIMEOUT).byActor("ANONYMOUS"));
        }
        LoginRedirects.toLogin(request, response, expired);
    }
}
