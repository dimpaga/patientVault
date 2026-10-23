package com.example.patientvault.search.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.example.patientvault.search.QueryType;
import com.example.patientvault.search.SearchProperties;
import com.example.patientvault.search.TextNormalizer;
import com.example.patientvault.shared.PhoneNumbers;

/**
 * Classifies the text typed in the search box (spec 6.3):
 * 11 digits -> AMKA; contains "@" -> e-mail; mostly digits/+/spaces with at least 6 digits -> mobile;
 * otherwise name words. Short or odd input is rejected with a message key, never an exception.
 */
final class SearchQueryParser {

    /** @param values lookup values: one token source for AMKA/e-mail/mobile, the (truncated) name prefixes for NAME */
    record Parsed(QueryType type, List<String> values, String messageKey, Object messageArg) {
        static Parsed of(QueryType type, List<String> values) {
            return new Parsed(type, values, null, null);
        }

        static Parsed rejected(String key, Object arg) {
            return new Parsed(QueryType.INVALID, List.of(), key, arg);
        }
    }

    private static final Pattern AMKA_COMPACT = Pattern.compile("\\d{11}");
    private static final Pattern PHONE_CHARS = Pattern.compile("[0-9+\\s().-]+");
    private static final int MIN_PHONE_DIGITS = 6;
    private static final int MAX_QUERY_LENGTH = 100;

    private final SearchProperties properties;

    SearchQueryParser(SearchProperties properties) {
        this.properties = properties;
    }

    Parsed parse(String raw) {
        String query = raw == null ? "" : raw.strip();
        if (query.isEmpty()) {
            return Parsed.of(QueryType.EMPTY, List.of());
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            return Parsed.rejected("search.message.tooLong", MAX_QUERY_LENGTH);
        }
        String compact = query.replaceAll("[\\s-]", "");
        if (AMKA_COMPACT.matcher(compact).matches()) {
            return Parsed.of(QueryType.AMKA, List.of(compact));
        }
        if (query.contains("@")) {
            return Parsed.of(QueryType.EMAIL, List.of(query.toLowerCase(Locale.ROOT)));
        }
        if (PHONE_CHARS.matcher(query).matches()) {
            long digits = query.chars().filter(Character::isDigit).count();
            if (digits < MIN_PHONE_DIGITS) {
                return Parsed.rejected("search.message.numberTooShort", MIN_PHONE_DIGITS);
            }
            return PhoneNumbers.toE164(query)
                    .map(e164 -> Parsed.of(QueryType.MOBILE, List.of(e164)))
                    .orElseGet(() -> Parsed.rejected("search.message.phoneInvalid", null));
        }
        return nameQuery(query);
    }

    private Parsed nameQuery(String query) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (String word : TextNormalizer.words(query)) {
            if (word.length() < properties.minPrefixLength()) {
                return Parsed.rejected("search.message.nameTooShort", properties.minPrefixLength());
            }
            // only prefixes up to the maximum length are indexed
            prefixes.add(word.substring(0, Math.min(word.length(), properties.maxPrefixLength())));
        }
        return prefixes.isEmpty() ? Parsed.of(QueryType.EMPTY, List.of()) : Parsed.of(QueryType.NAME, List.copyOf(prefixes));
    }
}
