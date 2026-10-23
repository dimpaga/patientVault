package com.example.patientvault.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

/** Bookkeeping on the doctor account around authentication: login timestamps and lockout. */
@Service
class UserAccountService {

    /** Lockouts that ended more than this long ago also forget their failure history. */
    static final Duration LOCKOUT_MEMORY = Duration.ofHours(24);

    private final DoctorUserRepository users;
    private final Clock clock;
    private final LockoutProperties lockout;
    private final AuditService audit;

    UserAccountService(DoctorUserRepository users, Clock clock, LockoutProperties lockout, AuditService audit) {
        this.users = users;
        this.clock = clock;
        this.lockout = lockout;
        this.audit = audit;
    }

    @Transactional
    void recordLoginSuccess(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(clock.instant());
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        });
    }

    /** Counts a failed password attempt for an existing user (unknown names are ignored) and locks on threshold. */
    @Transactional
    void recordFailedLogin(String username) {
        users.findByUsernameForUpdate(username).ifPresent(user -> {
            Instant now = clock.instant();
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                return;
            }
            int failures = user.getFailedAttempts() + 1;
            user.setFailedAttempts(failures);
            if (failures % lockout.maxAttempts() == 0) {
                Duration duration = lockDuration(failures / lockout.maxAttempts());
                user.setLockedUntil(now.plus(duration));
                audit.record(AuditEvent.denied(AuditAction.ACCOUNT_LOCKED).byActor("ANONYMOUS")
                        .onEntity("USER", user.getId())
                        .withDetail("failedAttempts", failures)
                        .withDetail("lockMinutes", duration.toMinutes()));
            }
        });
    }

    Duration lockDuration(int lockNumber) {
        long factor = 1L << Math.min(Math.max(lockNumber - 1, 0), 20);
        Duration duration = lockout.duration().multipliedBy(factor);
        return duration.compareTo(lockout.maxDuration()) > 0 ? lockout.maxDuration() : duration;
    }

    @Transactional
    int resetExpiredLockouts() {
        return users.resetLockoutsExpiredBefore(clock.instant().minus(LOCKOUT_MEMORY));
    }
}
