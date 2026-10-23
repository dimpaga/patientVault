package com.example.patientvault.identity.internal;

import java.io.IOException;
import java.time.Clock;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Login, failure and logout handlers. Responses are deliberately generic: no hint whether the user exists. */
final class LoginHandlers {

    private LoginHandlers() {
    }

    /**
     * Called once the password is correct. If a second factor is required the freshly authenticated
     * context is discarded and replaced by a {@link PendingMfa} marker: the session stays anonymous
     * until {@link LoginCompletion} runs after the second factor.
     */
    static class PasswordStep implements AuthenticationSuccessHandler {

        private final MfaProperties mfa;
        private final LoginCompletion completion;
        private final Clock clock;

        PasswordStep(MfaProperties mfa, LoginCompletion completion, Clock clock) {
            this.mfa = mfa;
            this.completion = completion;
            this.clock = clock;
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) throws IOException, ServletException {
            AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
            if (principal.isTotpEnabled() || mfa.required()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession();
                session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                session.setAttribute(SessionKeys.PENDING_MFA,
                        new PendingMfa(principal.getId(), principal.getUsername(), clock.instant(),
                                !principal.isTotpEnabled()));
                response.sendRedirect(request.getContextPath() + "/login/mfa");
                return;
            }
            completion.complete(request, response, principal.getId());
        }
    }

    static AuthenticationFailureHandler failure(AuditService audit) {
        return (request, response, exception) -> {
            // The attempted username is never recorded: users sometimes type their password into that field.
            audit.record(AuditEvent.failure(AuditAction.LOGIN_FAILURE).byActor("ANONYMOUS")
                    .withDetail("reason", exception instanceof LockedException ? "locked" : "bad_credentials"));
            response.sendRedirect(request.getContextPath() + "/login?error");
        };
    }

    static LogoutSuccessHandler logout(AuditService audit) {
        return (request, response, authentication) -> {
            if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
                audit.record(AuditEvent.success(AuditAction.LOGOUT).byActor(principal.getUsername())
                        .onEntity("USER", principal.getId()));
            }
            response.sendRedirect(request.getContextPath() + "/login?logout");
        };
    }
}
