package com.example.patientvault.identity;

/**
 * Permission-style authorities used in {@code @PreAuthorize("hasAuthority(...)")}. Code is protected by
 * permissions, never by raw role names, so a restricted role can be added later by changing the
 * role-to-permission mapping only.
 */
public final class Permissions {

    public static final String PATIENT_READ = "PATIENT_READ";
    public static final String PATIENT_WRITE = "PATIENT_WRITE";
    public static final String PATIENT_DELETE = "PATIENT_DELETE";
    public static final String FILE_READ = "FILE_READ";
    public static final String FILE_WRITE = "FILE_WRITE";
    public static final String AUDIT_READ = "AUDIT_READ";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

    private Permissions() {
    }
}
