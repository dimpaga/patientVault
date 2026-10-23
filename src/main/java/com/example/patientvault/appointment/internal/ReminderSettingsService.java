package com.example.patientvault.appointment.internal;

import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;
import com.example.patientvault.shared.DoctorContact;
import com.example.patientvault.shared.Permissions;

/** Settings-side operations on the reminder e-mail address. */
@Service
public class ReminderSettingsService {

    public enum Outcome { SENT, NO_ADDRESS, FAILED }

    private final DoctorContact contact;
    private final MailComposer composer;
    private final MailSender mail;
    private final AuditService audit;
    private final ReminderProperties properties;

    ReminderSettingsService(DoctorContact contact, MailComposer composer, MailSender mail, AuditService audit,
                            ReminderProperties properties) {
        this.contact = contact;
        this.composer = composer;
        this.mail = mail;
        this.audit = audit;
        this.properties = properties;
    }

    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    public Optional<String> notificationEmail() {
        return contact.notificationEmail();
    }

    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    public void saveNotificationEmail(String email) {
        contact.updateNotificationEmail(email);
        audit.record(AuditEvent.success(AuditAction.SETTINGS_CHANGED).withDetail("setting", "notificationEmail"));
    }

    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    public Outcome sendTest() {
        Optional<String> address = contact.notificationEmail();
        if (address.isEmpty()) {
            return Outcome.NO_ADDRESS;
        }
        try {
            mail.send(composer.test(address.get()));
            audit.record(AuditEvent.success(AuditAction.SETTINGS_CHANGED).withDetail("setting", "testEmail"));
            return Outcome.SENT;
        } catch (MailSendException e) {
            audit.record(AuditEvent.failure(AuditAction.SETTINGS_CHANGED).withDetail("setting", "testEmail")
                    .withDetail("error", e.errorClass()));
            return Outcome.FAILED;
        }
    }

    public ReminderProperties preferences() {
        return properties;
    }
}
