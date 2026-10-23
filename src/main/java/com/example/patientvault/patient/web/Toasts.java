package com.example.patientvault.patient.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** Builds the {@code HX-Trigger} header that makes the browser show a toast (see app.js "showToast"). */
@Component
class Toasts {

    private final MessageSource messages;

    Toasts(MessageSource messages) {
        this.messages = messages;
    }

    HttpHeaders success(String messageKey) {
        return header(messageKey, "success");
    }

    private HttpHeaders header(String messageKey, String level) {
        Locale locale = LocaleContextHolder.getLocale();
        String message = messages.getMessage(messageKey, null, messageKey, locale);
        HttpHeaders headers = new HttpHeaders();
        headers.set("HX-Trigger", "{\"showToast\":{\"message\":\"" + jsonAscii(message) + "\",\"level\":\"" + level + "\"}}");
        return headers;
    }

    /** Header values must be ASCII, so every non-ASCII character becomes a JSON unicode escape. */
    static String jsonAscii(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
