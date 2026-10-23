package com.example.patientvault.appointment.internal;

/** Delivery of an e-mail. Implementations: SMTP (default); an HTTP-API adapter can be added behind this port. */
public interface MailSender {

    /** @throws MailSendException when the message could not be handed over; the message carries no addresses */
    void send(MailMessage message);
}
