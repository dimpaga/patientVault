package com.example.patientvault.shared;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Outermost filter: assigns a request id (returned as {@code X-Request-Id}, put in the logging context and
 * exposed to the error pages) and writes one access-log line per request with the route template.
 * The access line never contains the query string, request or response bodies, or any header value.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger ACCESS = LoggerFactory.getLogger("access");
    /** A client-supplied id is accepted only when it cannot be used for log injection. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern ID_SEGMENT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9]+");
    private static final Pattern SAFE_PATH = Pattern.compile("/[A-Za-z0-9_./{}-]*");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        request.setAttribute(LogContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        MDC.put(LogContext.REQUEST_ID, requestId);
        MDC.put(LogContext.REMOTE_IP, request.getRemoteAddr());

        CountingResponse counted = new CountingResponse(response);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, counted);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            failed = true;
            throw e;
        } finally {
            logAccess(request, counted, failed, (System.nanoTime() - started) / 1_000_000);
            MDC.remove(LogContext.REQUEST_ID);
            MDC.remove(LogContext.REMOTE_IP);
            MDC.remove(LogContext.USER_ID);
        }
    }

    private static String requestId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Request-Id");
        return supplied != null && SAFE_ID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
    }

    private static void logAccess(HttpServletRequest request, CountingResponse response, boolean failed, long millis) {
        int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
        String route = route(request);
        // Platform health probes run every few seconds; keep them out of the INFO stream.
        Level level = route.startsWith("/actuator/health") ? Level.DEBUG : Level.INFO;
        ACCESS.atLevel(level)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", millis)
                .addKeyValue("bytes", response.bytesWritten())
                .log("{} {} {} {}ms {}B", request.getMethod(), route, status, millis, response.bytesWritten());
    }

    /**
     * The matched Spring MVC pattern (e.g. {@code /patients/{id}}). Requests that never reached a handler
     * (security redirects, unknown URLs) fall back to the path with id-like segments masked.
     */
    static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String template) {
            return template;
        }
        String path = request.getServletPath();
        if (path == null || path.length() > 100 || !SAFE_PATH.matcher(path).matches()) {
            return "UNMAPPED";
        }
        StringBuilder masked = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            masked.append('/').append(ID_SEGMENT.matcher(segment).matches() ? "{id}" : segment);
        }
        return masked.length() == 0 ? "/" : masked.toString();
    }
}
