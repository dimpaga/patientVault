package com.example.patientvault.search.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.crypto.BlindIndexer;
import com.example.patientvault.search.IndexedFields;
import com.example.patientvault.search.SearchIndex;
import com.example.patientvault.search.SearchProperties;
import com.example.patientvault.search.TextNormalizer;
import com.example.patientvault.search.TokenKind;

/**
 * Turns patient values into HMAC blind-index tokens (spec 6.3): one token each for AMKA, e-mail and
 * mobile, and one token per prefix (min..max characters) of every normalised name word.
 */
@Service
class DefaultSearchIndex implements SearchIndex {

    private final SearchTokenRepository tokens;
    private final BlindIndexer blindIndexer;
    private final SearchProperties properties;

    DefaultSearchIndex(SearchTokenRepository tokens, BlindIndexer blindIndexer, SearchProperties properties) {
        this.tokens = tokens;
        this.blindIndexer = blindIndexer;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void index(UUID patientId, IndexedFields fields) {
        Set<SearchTokenId> wanted = new LinkedHashSet<>();
        add(wanted, patientId, TokenKind.AMKA, normalizeAmka(fields.amkaDigits()));
        add(wanted, patientId, TokenKind.EMAIL, normalizeEmail(fields.email()));
        add(wanted, patientId, TokenKind.MOBILE, fields.mobileE164());
        for (String word : nameWords(fields)) {
            int longest = Math.min(properties.maxPrefixLength(), word.length());
            for (int length = properties.minPrefixLength(); length <= longest; length++) {
                add(wanted, patientId, TokenKind.NAME_PREFIX, word.substring(0, length));
            }
        }
        tokens.deleteAllForPatient(patientId);
        tokens.saveAll(wanted.stream().map(SearchToken::new).toList());
        tokens.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findByAmka(String amka) {
        return lookup(TokenKind.AMKA, normalizeAmka(amka)).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findByEmail(String email) {
        return lookup(TokenKind.EMAIL, normalizeEmail(email));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findByMobile(String mobileE164) {
        return lookup(TokenKind.MOBILE, mobileE164);
    }

    private List<UUID> lookup(TokenKind kind, String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return tokens.findPatientIds(kind.name(), blindIndexer.token(kind.name(), value), PageRequest.of(0, 1000));
    }

    private void add(Set<SearchTokenId> target, UUID patientId, TokenKind kind, String value) {
        if (value != null && !value.isBlank()) {
            target.add(new SearchTokenId(patientId, kind.name(), blindIndexer.token(kind.name(), value)));
        }
    }

    private static List<String> nameWords(IndexedFields fields) {
        Set<String> words = new LinkedHashSet<>();
        words.addAll(TextNormalizer.words(fields.firstName()));
        words.addAll(TextNormalizer.words(fields.lastName()));
        return List.copyOf(words);
    }

    private static String normalizeAmka(String amka) {
        return amka == null ? null : amka.replaceAll("\\D", "");
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
