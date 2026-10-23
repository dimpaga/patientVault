package com.example.patientvault.identity.internal;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.filter.OncePerRequestFilter;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP rate limit (in-memory token buckets) on the credential endpoints. Exceeding it redirects back
 * with {@code ?throttled} and answers {@code Retry-After}; the hit is audited without the attempted values.
 */
class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/login", "/login/mfa");
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final RateLimitProperties properties;
    private final AuditService audit;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    AuthRateLimitFilter(RateLimitProperties properties, AuditService audit) {
        this.properties = properties;
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ConsumptionProbe probe = bucketFor(request.getRemoteAddr()).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        audit.record(AuditEvent.denied(AuditAction.RATE_LIMITED).byActor("ANONYMOUS")
                .withDetail("path", request.getServletPath()));
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        if (LoginRedirects.isHtmx(request)) {
            response.setStatus(429);
        } else {
            response.sendRedirect(request.getContextPath() + request.getServletPath() + "?throttled");
        }
    }

    private Bucket bucketFor(String client) {
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear();
        }
        return buckets.computeIfAbsent(client, key -> Bucket.builder()
                .addLimit(limit -> limit.capacity(properties.attempts())
                        .refillGreedy(properties.attempts(), properties.window()))
                .build());
    }
}
