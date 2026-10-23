package com.example.patientvault.identity.internal;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated principal kept in the session. The password hash is erased after authentication. */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String username;
    private String passwordHash;
    private final boolean locked;
    private final boolean totpEnabled;
    private final boolean mustChangePassword;
    private final ArrayList<GrantedAuthority> authorities;

    AppUserPrincipal(DoctorUser user, Instant now, Collection<GrantedAuthority> authorities) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        this.totpEnabled = user.isTotpEnabled();
        this.mustChangePassword = user.isMustChangePassword();
        this.authorities = new ArrayList<>(authorities);
    }

    public UUID getId() {
        return id;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AppUserPrincipal other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + "]";
    }
}
