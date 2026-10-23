package com.example.patientvault.appointment;

import java.util.List;

/** Public facade of the appointment module (used by the dashboard). */
public interface AppointmentApi {

    /** Today's SCHEDULED appointments in the configured time zone, earliest first. */
    List<AppointmentSummary> today();

    /** Reminders that ended FAILED in the last 7 days (shown as a dashboard alert). */
    long recentFailedReminders();
}
