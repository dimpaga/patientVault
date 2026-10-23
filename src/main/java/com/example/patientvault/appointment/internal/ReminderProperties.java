package com.example.patientvault.appointment.internal;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reminder behaviour (spec 8.6 / 15).
 *
 * @param leadTimes      ISO-8601 amounts before the appointment. A date-based amount such as {@code P1D} means the
 *                       same local time on the previous day (stays correct across DST changes); a time-based one such
 *                       as {@code PT1H} is an exact duration
 * @param privacyMode    {@code FULL_NAME} or {@code INITIALS} in the e-mail
 * @param maxAttempts    sends tried before a reminder is marked FAILED
 * @param retryBaseDelay first retry delay; doubles with every further attempt
 * @param batchSize      reminders handled per scheduler run
 * @param locale         language of the e-mails (the scheduler has no browser language)
 * @param publicBaseUrl  base of the deep link in the e-mail
 */
@ConfigurationProperties(prefix = "app.reminders")
public record ReminderProperties(
        @DefaultValue({"P1D", "PT1H"}) List<String> leadTimes,
        @DefaultValue("FULL_NAME") PrivacyMode privacyMode,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("1m") Duration retryBaseDelay,
        @DefaultValue("20") int batchSize,
        @DefaultValue("en") String locale,
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        @DefaultValue DailyDigest dailyDigest) {

    public enum PrivacyMode { FULL_NAME, INITIALS }

    /** Optional morning e-mail listing the day's appointments. */
    public record DailyDigest(@DefaultValue("true") boolean enabled, @DefaultValue("07:30") LocalTime at) {
    }
}
