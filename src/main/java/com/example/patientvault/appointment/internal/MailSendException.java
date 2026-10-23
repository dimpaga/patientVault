package com.example.patientvault.appointment.internal;

/** Sending failed. Only the technical class of the failure is kept (SMTP replies can echo addresses). */
public class MailSendException extends RuntimeException {

    private final String errorClass;

    public MailSendException(Throwable cause) {
        super("Mail could not be sent");
        this.errorClass = cause == null ? "Unknown" : cause.getClass().getSimpleName();
    }

    public String errorClass() {
        return errorClass;
    }
}
