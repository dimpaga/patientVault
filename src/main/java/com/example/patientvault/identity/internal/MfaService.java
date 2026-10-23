package com.example.patientvault.identity.internal;

import java.time.Clock;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;
import com.example.patientvault.crypto.CryptoService;

/** TOTP enrolment and second-factor verification (TOTP code or single-use recovery code). */
@Service
public class MfaService {

    public enum Outcome { TOTP_OK, RECOVERY_OK, INVALID, LOCKED }

    /** @param secretDisplay the secret in groups of four for manual entry */
    public record Enrolment(String secret, String secretDisplay, String otpauthUri) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            return "Enrolment[...]";
        }
    }

    private final DoctorUserRepository users;
    private final TotpService totp;
    private final RecoveryCodeService recoveryCodes;
    private final CryptoService crypto;
    private final AuditService audit;
    private final Clock clock;
    private final UserAccountService accounts;

    MfaService(DoctorUserRepository users, TotpService totp, RecoveryCodeService recoveryCodes,
               CryptoService crypto, AuditService audit, Clock clock, UserAccountService accounts) {
        this.users = users;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.crypto = crypto;
        this.audit = audit;
        this.clock = clock;
        this.accounts = accounts;
    }

    public Enrolment newEnrolment(String username) {
        String secret = totp.newSecret();
        return new Enrolment(secret, groupsOfFour(secret), totp.otpauthUri(username, secret));
    }

    public String qrSvg(String otpauthUri) {
        return QrCodes.svg(otpauthUri);
    }

    /** Confirms the first code of a new secret, stores it encrypted and returns the fresh recovery codes. */
    @Transactional
    public Optional<List<String>> confirmEnrolment(UUID userId, String secret, String code) {
        DoctorUser user = users.findById(userId).orElseThrow();
        if (user.isTotpEnabled()) {
            return Optional.empty();
        }
        long step = totp.verify(secret, clean(code), clock.instant(), null);
        if (step < 0) {
            audit.record(AuditEvent.failure(AuditAction.MFA_FAILURE).byActor(user.getUsername())
                    .onEntity("USER", userId).withDetail("phase", "enrolment"));
            return Optional.empty();
        }
        user.setTotpSecretEnc(crypto.encryptSecretText(secretContext(userId), secret));
        user.setTotpEnabled(true);
        user.setTotpLastStep(step);
        List<String> codes = recoveryCodes.regenerate(userId);
        audit.record(AuditEvent.success(AuditAction.MFA_ENROLLED).byActor(user.getUsername()).onEntity("USER", userId));
        return Optional.of(codes);
    }

    /** Verifies the second factor at login; failures count towards the account lockout like bad passwords. */
    @Transactional
    public Outcome verifyLogin(UUID userId, String rawCode) {
        DoctorUser user = users.findById(userId).orElseThrow();
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return Outcome.LOCKED;
        }
        String code = clean(rawCode);
        Outcome outcome = Outcome.INVALID;
        if (TotpService.looksLikeTotp(code) && user.isTotpEnabled() && user.getTotpSecretEnc() != null) {
            String secret = crypto.decryptSecretText(secretContext(userId), user.getTotpSecretEnc());
            long step = totp.verify(secret, code, now, user.getTotpLastStep());
            if (step >= 0) {
                user.setTotpLastStep(step);
                outcome = Outcome.TOTP_OK;
            }
        } else if (!TotpService.looksLikeTotp(code) && recoveryCodes.consume(userId, code)) {
            outcome = Outcome.RECOVERY_OK;
        }

        AuditEvent event = outcome == Outcome.INVALID
                ? AuditEvent.failure(AuditAction.MFA_FAILURE) : AuditEvent.success(AuditAction.MFA_SUCCESS);
        audit.record(event.byActor(user.getUsername()).onEntity("USER", userId)
                .withDetail("method", outcome == Outcome.RECOVERY_OK ? "recovery" : "totp"));
        if (outcome == Outcome.INVALID) {
            accounts.recordFailedLogin(user.getUsername());
        }
        return outcome;
    }

    private static String clean(String code) {
        return code == null ? "" : code.strip().replace(" ", "");
    }

    static String secretContext(UUID userId) {
        return "totp:" + userId;
    }

    private static String groupsOfFour(String secret) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < secret.length(); i += 4) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(secret, i, Math.min(i + 4, secret.length()));
        }
        return sb.toString();
    }
}
