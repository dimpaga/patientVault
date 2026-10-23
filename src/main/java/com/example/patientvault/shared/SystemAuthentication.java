package com.example.patientvault.shared;

import java.util.function.Supplier;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Runs scheduled work (reminders, digest, housekeeping) with an explicit, minimal authority set. Background jobs
 * have no logged-in user, but the services they call are protected by method security; instead of switching that
 * off, a job names exactly the permissions it needs. The previous context is restored afterwards.
 */
public final class SystemAuthentication {

    public static final String ACTOR = "SYSTEM";

    private SystemAuthentication() {
    }

    public static <T> T run(Supplier<T> action, String... permissions) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        GrantedAuthority[] authorities = java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(ACTOR, null, java.util.List.of(authorities)));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    public static void run(Runnable action, String... permissions) {
        run(() -> {
            action.run();
            return null;
        }, permissions);
    }
}
