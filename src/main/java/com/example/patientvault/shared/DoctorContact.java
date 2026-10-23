package com.example.patientvault.shared;

import java.util.Optional;

/**
 * The doctor's personal e-mail address where reminders are sent. A port in the shared kernel: implemented by
 * the identity module (which stores it encrypted), used by the appointment module.
 */
public interface DoctorContact {

    /** Stored address, or the one from {@code APP_DOCTOR_NOTIFICATION_EMAIL} until the doctor saves another. */
    Optional<String> notificationEmail();

    /** Stores a new, already validated and normalised address (encrypted). */
    void updateNotificationEmail(String email);
}
