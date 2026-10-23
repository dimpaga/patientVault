package com.example.patientvault.appointment.internal;

import java.util.List;

/** An outgoing e-mail. {@code toString()} prints nothing (addresses and content are personal data). */
public record MailMessage(String to, String subject, String text, String html, List<Attachment> attachments) {

    public record Attachment(String fileName, String contentType, byte[] content) {
    }

    @Override
    public String toString() {
        return "MailMessage[...]";
    }
}
