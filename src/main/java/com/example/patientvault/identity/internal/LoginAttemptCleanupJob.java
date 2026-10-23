package com.example.patientvault.identity.internal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

/** Hourly: clears lockouts that ended more than 24 hours ago (also forgetting their failure history). */
@Component
class LoginAttemptCleanupJob {

    private final UserAccountService accounts;
    private final AuditService audit;

    LoginAttemptCleanupJob(UserAccountService accounts, AuditService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @Scheduled(cron = "0 0 * * * *")
    void run() {
        int reset = accounts.resetExpiredLockouts();
        audit.record(AuditEvent.success(AuditAction.JOB_RUN).byActor("SYSTEM")
                .withDetail("job", "LoginAttemptCleanupJob").withDetail("reset", reset));
    }
}
