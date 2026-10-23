package com.example.patientvault.identity.internal;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

/** Fresh proof of identity (password + TOTP/recovery code) required before sensitive actions. */
@Service
public class ReauthService {

    public enum Result { OK, INVALID, LOCKED }

    private final DoctorUserRepository users;
    private final PasswordEncoder encoder;
    private final MfaService mfa;
    private final UserAccountService accounts;
    private final AuditService audit;
    private final Clock clock;

    ReauthService(DoctorUserRepository users, PasswordEncoder encoder, MfaService mfa, UserAccountService accounts,
                  AuditService audit, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.mfa = mfa;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result verify(UUID userId, String password, String code) {
        DoctorUser user = users.findById(userId).orElseThrow();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(clock.instant())) {
            return Result.LOCKED;
        }
        boolean ok = password != null && encoder.matches(password, user.getPasswordHash());
        if (ok && user.isTotpEnabled()) {
            MfaService.Outcome outcome = mfa.check(user, code, clock.instant());
            ok = outcome == MfaService.Outcome.TOTP_OK || outcome == MfaService.Outcome.RECOVERY_OK;
        }
        audit.record((ok ? AuditEvent.success(AuditAction.REAUTH_SUCCESS) : AuditEvent.failure(AuditAction.REAUTH_FAILURE))
                .byActor(user.getUsername()).onEntity("USER", userId));
        if (!ok) {
            accounts.recordFailedLogin(user.getUsername());
        }
        return ok ? Result.OK : Result.INVALID;
    }
}
