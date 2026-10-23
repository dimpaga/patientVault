package com.example.patientvault.identity.internal;

import java.time.Clock;
import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads the doctor account for authentication and transparently upgrades outdated password hashes. */
@Service
class AppUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final DoctorUserRepository users;
    private final Clock clock;

    AppUserDetailsService(DoctorUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        DoctorUser user = users.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("unknown user"));
        return new AppUserPrincipal(user, clock.instant(), RolePermissions.authoritiesFor(RolePermissions.DOCTOR));
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails principal, String newPassword) {
        DoctorUser user = users.findByUsername(principal.getUsername()).orElseThrow();
        user.setPasswordHash(newPassword);
        return new AppUserPrincipal(users.save(user), clock.instant(),
                RolePermissions.authoritiesFor(RolePermissions.DOCTOR));
    }
}
