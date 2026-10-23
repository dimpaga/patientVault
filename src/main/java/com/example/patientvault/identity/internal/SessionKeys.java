package com.example.patientvault.identity.internal;

/** Names of the session attributes used by the identity module. */
public final class SessionKeys {

    public static final String PENDING_MFA = "pv.pendingMfa";
    public static final String ENROL_SECRET = "pv.enrolSecret";
    public static final String LOGIN_AT = "pv.loginAt";
    public static final String REAUTH_AT = "pv.reauthAt";
    public static final String RECOVERY_CODES = "pv.recoveryCodes";

    private SessionKeys() {
    }
}
