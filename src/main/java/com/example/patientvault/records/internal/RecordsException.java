package com.example.patientvault.records.internal;

/**
 * A rejected request in records. {@code messageKey} is a message-bundle key the web layer shows to the doctor;
 * it never contains file names or content. {@code status} is the HTTP status to answer with.
 */
public class RecordsException extends RuntimeException {

    private final String messageKey;
    private final Object messageArg;
    private final int status;

    public RecordsException(int status, String messageKey, Object messageArg) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
        this.messageArg = messageArg;
    }

    public static RecordsException badRequest(String key) {
        return new RecordsException(400, key, null);
    }

    public static RecordsException badRequest(String key, Object arg) {
        return new RecordsException(400, key, arg);
    }

    public static RecordsException conflict(String key) {
        return new RecordsException(409, key, null);
    }

    public static RecordsException notFound() {
        return new RecordsException(404, "records.error.notFound", null);
    }

    public int status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }

    public Object messageArg() {
        return messageArg;
    }
}
