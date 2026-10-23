package com.example.patientvault.identity.internal;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Component;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * The single place where a session becomes authenticated, after all required factors have passed:
 * rotates the session id, stores the security context, records the login and audits it.
 */
@Component
public class LoginCompletion {

    private final DoctorUserRepository users;
    private final UserAccountService accounts;
    private final AuditService audit;
    private final Clock clock;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SavedRequestAwareAuthenticationSuccessHandler redirectHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();

    LoginCompletion(DoctorUserRepository users, UserAccountService accounts, AuditService audit, Clock clock,
                    RequestCache requestCache) {
        this.users = users;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
        this.redirectHandler.setRequestCache(requestCache);
        this.redirectHandler.setDefaultTargetUrl("/");
    }

    /** Authenticates the session without redirecting (the caller chooses where to go next). */
    public Authentication establish(HttpServletRequest request, HttpServletResponse response, UUID userId) {
        DoctorUser user = users.findById(userId).orElseThrow();
        AppUserPrincipal principal = new AppUserPrincipal(user, clock.instant(),
                RolePermissions.authoritiesFor(RolePermissions.DOCTOR));
        principal.eraseCredentials();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        HttpSession session = request.getSession();
        session.removeAttribute(SessionKeys.PENDING_MFA);
        session.removeAttribute(SessionKeys.ENROL_SECRET);
        session.setAttribute(SessionKeys.LOGIN_AT, clock.instant());
        session.setAttribute(SessionKeys.REAUTH_AT, clock.instant());
        session.setAttribute(SessionKeys.MUST_CHANGE_PASSWORD, user.isMustChangePassword());

        accounts.recordLoginSuccess(userId);
        audit.record(AuditEvent.success(AuditAction.LOGIN_SUCCESS).byActor(principal.getUsername())
                .onEntity("USER", userId));
        return authentication;
    }

    /** Authenticates the session and redirects to the originally requested page (or the dashboard). */
    public void complete(HttpServletRequest request, HttpServletResponse response, UUID userId)
            throws IOException, ServletException {
        Authentication authentication = establish(request, response, userId);
        redirectHandler.onAuthenticationSuccess(request, response, authentication);
    }
}
