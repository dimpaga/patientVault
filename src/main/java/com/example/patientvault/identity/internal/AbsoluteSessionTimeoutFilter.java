package com.example.patientvault.identity.internal;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.web.filter.OncePerRequestFilter;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Ends sessions older than the absolute timeout (default 8 h) regardless of activity. */
class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Duration maxAge;
    private final Clock clock;
    private final AuditService audit;

    AbsoluteSessionTimeoutFilter(Duration maxAge, Clock clock, AuditService audit) {
        this.maxAge = maxAge;
        this.clock = clock;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionKeys.LOGIN_AT) instanceof Instant loginAt
                && loginAt.plus(maxAge).isBefore(clock.instant())) {
            audit.record(AuditEvent.success(AuditAction.SESSION_TIMEOUT).byActor("ANONYMOUS")
                    .withDetail("reason", "absolute"));
            session.invalidate();
            LoginRedirects.toLogin(request, response, true);
            return;
        }
        chain.doFilter(request, response);
    }
}
