package com.example.patientvault.appointment.internal;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

/**
 * SMTP adapter on Spring's {@link JavaMailSender}. STARTTLS is required by default
 * ({@code mail.smtp.starttls.required=true}); the local profile turns it off for the Mailpit sink only.
 */
@Component
class SmtpMailSender implements MailSender {

    private final JavaMailSender mail;
    private final String from;

    SmtpMailSender(JavaMailSender mail, @Value("${app.mail.from}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(MailMessage message) {
        try {
            MimeMessage mime = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            for (MailMessage.Attachment attachment : message.attachments()) {
                helper.addAttachment(attachment.fileName(),
                        new org.springframework.core.io.ByteArrayResource(attachment.content()), attachment.contentType());
            }
            mail.send(mime);
        } catch (Exception e) {
            throw new MailSendException(e);
        }
    }
}
