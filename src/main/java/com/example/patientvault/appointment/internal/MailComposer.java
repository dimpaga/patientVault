package com.example.patientvault.appointment.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.example.patientvault.patient.PatientSummary;

/**
 * Builds the e-mails (spec 8.6). Because e-mail is not end-to-end encrypted, reminders contain only: the date and
 * time, the patient's name (or initials), and a link that needs a login. Never the AMKA, phone, diagnosis, notes,
 * the appointment reason or file names - this class has no access to them.
 */
@Component
class MailComposer {

    /** One label/value line of the table in the mail body. */
    record Row(String label, String value) {
    }

    private final MessageSource messages;
    private final SpringTemplateEngine templates;
    private final ReminderProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    MailComposer(MessageSource messages, SpringTemplateEngine templates, ReminderProperties properties, Clock clock,
                 @Value("${app.timezone}") ZoneId zone) {
        this.messages = messages;
        this.templates = templates;
        this.properties = properties;
        this.clock = clock;
        this.zone = zone;
    }

    MailMessage reminder(String to, UUID appointmentId, Instant scheduledAt, int durationMinutes, PatientSummary patient) {
        Locale locale = locale();
        String name = displayName(patient);
        String when = date(scheduledAt, locale) + ", " + time(scheduledAt, locale);
        String link = link(appointmentId);
        String title = text("mail.reminder.title", locale);
        List<Row> rows = List.of(new Row(text("mail.when", locale), when), new Row(text("mail.patient", locale), name));
        MailMessage.Attachment ics = IcsBuilder.build(appointmentId, scheduledAt, durationMinutes,
                text("mail.ics.summary", locale, name), link, clock.instant());
        return build(to, text("mail.reminder.subject", locale, time(scheduledAt, locale), date(scheduledAt, locale)),
                locale, title, text("mail.reminder.intro", locale), rows, text("mail.open", locale), link, List.of(ics));
    }

    /** The day's agenda; {@code items} are (time, display name) pairs already in the doctor's zone. */
    MailMessage digest(String to, LocalDate day, List<DigestItem> items) {
        Locale locale = locale();
        List<Row> rows = new ArrayList<>();
        for (DigestItem item : items) {
            rows.add(new Row(item.time().format(DateTimeFormatter.ofPattern("HH:mm", locale)), displayName(item.patient())));
        }
        String dayText = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale));
        return build(to, text("mail.digest.subject", locale, dayText), locale, text("mail.digest.title", locale, dayText),
                text("mail.digest.intro", locale, items.size()), rows, text("mail.open.agenda", locale),
                properties.publicBaseUrl() + "/appointments", List.of());
    }

    MailMessage test(String to) {
        Locale locale = locale();
        return build(to, text("mail.test.subject", locale), locale, text("mail.test.title", locale),
                text("mail.test.intro", locale), List.of(), text("mail.open.agenda", locale),
                properties.publicBaseUrl() + "/appointments", List.of());
    }

    record DigestItem(java.time.LocalTime time, PatientSummary patient) {
    }

    // ---- helpers ------------------------------------------------------------------------------

    private MailMessage build(String to, String subject, Locale locale, String title, String intro, List<Row> rows,
                              String buttonLabel, String url, List<MailMessage.Attachment> attachments) {
        String footer = text("mail.footer", locale);
        StringBuilder plain = new StringBuilder(title).append("\n\n").append(intro).append("\n\n");
        rows.forEach(r -> plain.append(r.label()).append(": ").append(r.value()).append('\n'));
        plain.append('\n').append(buttonLabel).append(": ").append(url).append("\n\n").append(footer).append('\n');

        Context context = new Context(locale);
        context.setVariable("title", title);
        context.setVariable("intro", intro);
        context.setVariable("rows", rows);
        context.setVariable("buttonLabel", buttonLabel);
        context.setVariable("url", url);
        context.setVariable("footer", footer);
        String html = templates.process("email/mail", context);
        return new MailMessage(to, subject, plain.toString(), html, attachments);
    }

    /** Full name, or initials such as "M. A." when {@code app.reminders.privacy-mode=INITIALS}. */
    String displayName(PatientSummary patient) {
        if (properties.privacyMode() == ReminderProperties.PrivacyMode.INITIALS) {
            return initial(patient.firstName()) + " " + initial(patient.lastName());
        }
        return patient.fullName();
    }

    private static String initial(String name) {
        String trimmed = name == null ? "" : name.strip();
        return trimmed.isEmpty() ? "" : new String(Character.toChars(trimmed.codePointAt(0))).toUpperCase(Locale.ROOT) + ".";
    }

    private String link(UUID appointmentId) {
        return properties.publicBaseUrl() + "/appointments/" + appointmentId;
    }

    private String date(Instant instant, Locale locale) {
        return instant.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale));
    }

    private String time(Instant instant, Locale locale) {
        return instant.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", locale));
    }

    private Locale locale() {
        return Locale.forLanguageTag(properties.locale());
    }

    private String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, key, locale);
    }
}
