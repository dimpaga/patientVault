package com.example.patientvault.identity.internal;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Login, failure and logout handlers. Responses are deliberately generic: no hint whether the user exists. */
final class LoginHandlers {

    private LoginHandlers() {
    }

    static class Success extends SavedRequestAwareAuthenticationSuccessHandler {

        private final UserAccountService accounts;
        private final AuditService audit;

        Success(UserAccountService accounts, AuditService audit) {
            this.accounts = accounts;
            this.audit = audit;
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) throws IOException, ServletException {
            AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
            accounts.recordLoginSuccess(principal.getId());
            audit.record(AuditEvent.success(AuditAction.LOGIN_SUCCESS).byActor(principal.getUsername())
                    .onEntity("USER", principal.getId()));
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }

    static AuthenticationFailureHandler failure(AuditService audit) {
        return (request, response, exception) -> {
            // The attempted username is never recorded: users sometimes type their password into that field.
            audit.record(AuditEvent.failure(AuditAction.LOGIN_FAILURE).byActor("ANONYMOUS"));
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
