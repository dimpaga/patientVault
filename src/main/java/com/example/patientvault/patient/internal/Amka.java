package com.example.patientvault.patient.internal;

import java.time.DateTimeException;
import java.time.LocalDate;

/** Greek social security number (AMKA): 11 digits, Luhn checksum, first six digits are the birth date DDMMYY. */
final class Amka {

    static final int LENGTH = 11;

    private Amka() {
    }

    /** Digits only (spaces, dashes and any other separators removed). */
    static String normalize(String input) {
        return input == null ? "" : input.replaceAll("\\D", "");
    }

    static boolean hasValidLength(String digits) {
        return digits.length() == LENGTH;
    }

    /** Standard Luhn check over all 11 digits (the last digit is the check digit). */
    static boolean hasValidChecksum(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** True when DDMMYY is a real calendar date in the 1900s or the 2000s. */
    static boolean hasParsableBirthDate(String digits) {
        int day = Integer.parseInt(digits.substring(0, 2));
        int month = Integer.parseInt(digits.substring(2, 4));
        int yy = Integer.parseInt(digits.substring(4, 6));
        return isDate(1900 + yy, month, day) || isDate(2000 + yy, month, day);
    }

    private static boolean isDate(int year, int month, int day) {
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }
}
