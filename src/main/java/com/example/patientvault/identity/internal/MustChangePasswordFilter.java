package com.example.patientvault.identity.internal;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Until the bootstrap/reset password has been changed, the only usable pages are the password form and logout. */
class MustChangePasswordFilter extends OncePerRequestFilter {

    static final String PASSWORD_PATH = "/settings/password";

    private static final List<String> ALLOWED_PREFIXES = List.of(
            PASSWORD_PATH, "/reauth", "/logout", "/error", "/mfa/recovery-codes", "/css/", "/js/", "/webjars/", "/icons/",
            "/actuator/", "/login");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal && session != null
                && Boolean.TRUE.equals(session.getAttribute(SessionKeys.MUST_CHANGE_PASSWORD))
                && !isAllowed(request.getServletPath())) {
            String url = request.getContextPath() + PASSWORD_PATH;
            if (LoginRedirects.isHtmx(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("HX-Redirect", url);
            } else {
                response.sendRedirect(url);
            }
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAllowed(String path) {
        return ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
