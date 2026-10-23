package com.example.patientvault.identity.internal;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.patientvault.identity.RequiresRecentAuth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Enforces {@link RequiresRecentAuth}: sends the user to /reauth when the last authentication is too old. */
@Configuration(proxyBeanMethods = false)
class RecentAuthConfig implements WebMvcConfigurer {

    private final Clock clock;
    private final Duration window;

    RecentAuthConfig(Clock clock, @Value("${app.security.reauth-window:5m}") Duration window) {
        this.clock = clock;
        this.window = window;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws IOException {
                if (!(handler instanceof HandlerMethod method) || !requiresRecentAuth(method)) {
                    return true;
                }
                HttpSession session = request.getSession(false);
                Object at = session == null ? null : session.getAttribute(SessionKeys.REAUTH_AT);
                if (at instanceof Instant instant && instant.plus(window).isAfter(clock.instant())) {
                    return true;
                }
                String next = "GET".equals(request.getMethod()) ? requestTarget(request) : "/";
                String url = request.getContextPath() + "/reauth?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8);
                if (LoginRedirects.isHtmx(request)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader("HX-Redirect", url);
                } else {
                    response.sendRedirect(url);
                }
                return false;
            }
        });
    }

    private static boolean requiresRecentAuth(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), RequiresRecentAuth.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RequiresRecentAuth.class);
    }

    private static String requestTarget(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI().substring(request.getContextPath().length()) + (query == null ? "" : "?" + query);
    }
}
