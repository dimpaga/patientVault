package com.example.patientvault.appointment.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Minimal RFC 5545 calendar entry for one appointment. Contains the display name and a link, never the reason. */
final class IcsBuilder {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private IcsBuilder() {
    }

    static MailMessage.Attachment build(UUID appointmentId, Instant start, int durationMinutes, String summary,
                                        String link, Instant now) {
        String text = String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//PatientVault//Appointments//EN",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:" + appointmentId + "@patientvault",
                "DTSTAMP:" + UTC.format(now),
                "DTSTART:" + UTC.format(start),
                "DTEND:" + UTC.format(start.plusSeconds(durationMinutes * 60L)),
                fold("SUMMARY:" + escape(summary)),
                fold("DESCRIPTION:" + escape(link)),
                "END:VEVENT",
                "END:VCALENDAR") + "\r\n";
        return new MailMessage.Attachment("appointment.ics", "text/calendar; charset=UTF-8; method=PUBLISH",
                text.getBytes(StandardCharsets.UTF_8));
    }

    /** Escapes backslash, semicolon, comma and line breaks as RFC 5545 requires for TEXT values. */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    /** Lines are limited to 75 octets; continuation lines start with a space. */
    static String fold(String line) {
        StringBuilder out = new StringBuilder();
        int octets = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > 75) {
                out.append("\r\n ");
                octets = 1;
            }
            out.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}
