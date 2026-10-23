package com.example.patientvault.search;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises free text before it is turned into blind-index tokens or search terms:
 * Unicode NFD, accents/tonos removed, lower-cased ({@link Locale#ROOT}), Greek final sigma mapped to
 * sigma and whitespace collapsed. {@code Παγκάλος}, {@code ΠΑΓΚΑΛΟΣ} and {@code παγκαλοσ} all normalise
 * to the same value.
 */
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        String stripped = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String lower = stripped.toLowerCase(Locale.ROOT).replace('ς', 'σ');
        return WHITESPACE.matcher(lower).replaceAll(" ").strip();
    }

    /** Normalised words of the input, split on whitespace; empty list for blank input. */
    public static List<String> words(String input) {
        String normalized = normalize(input);
        return normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
    }
}
