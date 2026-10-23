package com.example.patientvault.identity.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;

@Service
public class PasswordService {

    private final DoctorUserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final UserAccountService accounts;
    private final AuditService audit;

    PasswordService(DoctorUserRepository users, PasswordEncoder encoder, PasswordPolicy policy,
                    UserAccountService accounts, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.accounts = accounts;
        this.audit = audit;
    }

    /** @return message keys describing what is wrong; empty when the password was changed */
    @Transactional
    public List<String> change(UUID userId, String current, String next, String confirmation) {
        DoctorUser user = users.findById(userId).orElseThrow();
        if (current == null || !encoder.matches(current, user.getPasswordHash())) {
            accounts.recordFailedLogin(user.getUsername());
            return List.of("password.current.invalid");
        }
        List<String> problems = new ArrayList<>();
        if (next == null || !next.equals(confirmation)) {
            problems.add("password.confirm.mismatch");
        }
        problems.addAll(policy.violations(next, user.getUsername()));
        if (next != null && encoder.matches(next, user.getPasswordHash())) {
            problems.add("password.policy.same");
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        user.setPasswordHash(encoder.encode(next));
        user.setMustChangePassword(false);
        audit.record(AuditEvent.success(AuditAction.PASSWORD_CHANGED).byActor(user.getUsername())
                .onEntity("USER", userId));
        return List.of();
    }
}
