package com.example.patientvault.patient.internal;

import java.util.Optional;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/** Parses phone numbers with libphonenumber (default region GR) and returns E.164. */
final class PhoneNumbers {

    static final String DEFAULT_REGION = "GR";
    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumbers() {
    }

    /** {@code 6912345678}, {@code +30 691 234 5678} and {@code 00306912345678} all become {@code +306912345678}. */
    static Optional<String> toE164(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            PhoneNumber number = UTIL.parse(input.strip(), DEFAULT_REGION);
            if (!UTIL.isValidNumber(number)) {
                return Optional.empty();
            }
            return Optional.of(UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }
}
