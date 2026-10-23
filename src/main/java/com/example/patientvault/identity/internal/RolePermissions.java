package com.example.patientvault.identity.internal;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.example.patientvault.identity.Permissions;

/** Central role-to-permission map. Adding a restricted role later is a change to this map only. */
final class RolePermissions {

    static final String DOCTOR = "ROLE_DOCTOR";

    private static final Map<String, Set<String>> PERMISSIONS = Map.of(
            DOCTOR, Set.of(
                    Permissions.PATIENT_READ, Permissions.PATIENT_WRITE, Permissions.PATIENT_DELETE,
                    Permissions.FILE_READ, Permissions.FILE_WRITE,
                    Permissions.AUDIT_READ, Permissions.SETTINGS_MANAGE));

    private RolePermissions() {
    }

    /** The role itself plus every permission it grants. */
    static Collection<GrantedAuthority> authoritiesFor(String role) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority(role));
        List<String> permissions = PERMISSIONS.getOrDefault(role, Set.of()).stream().sorted().toList();
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        return authorities;
    }
}
