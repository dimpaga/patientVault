package com.example.patientvault.appointment.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** Builds the {@code HX-Trigger} header value that makes the browser show a toast (see app.js "showToast"). */
@Component
class AppointmentToasts {

    private final MessageSource messages;

    AppointmentToasts(MessageSource messages) {
        this.messages = messages;
    }

    String success(String messageKey) {
        return value(messageKey, "success");
    }

    String danger(String messageKey) {
        return value(messageKey, "danger");
    }

    private String value(String messageKey, String level) {
        Locale locale = LocaleContextHolder.getLocale();
        String message = messages.getMessage(messageKey, null, messageKey, locale);
        return "{\"showToast\":{\"message\":\"" + jsonAscii(message) + "\",\"level\":\"" + level + "\"}}";
    }

    /** Header values must be ASCII, so every non-ASCII character becomes a JSON unicode escape. */
    private static String jsonAscii(String text) {
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
