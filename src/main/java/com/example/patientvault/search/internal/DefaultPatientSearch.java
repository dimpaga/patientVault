package com.example.patientvault.search.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.crypto.BlindIndexer;
import com.example.patientvault.search.PatientSearch;
import com.example.patientvault.search.QueryType;
import com.example.patientvault.search.SearchProperties;
import com.example.patientvault.search.SearchResult;
import com.example.patientvault.search.TokenKind;

@Service
class DefaultPatientSearch implements PatientSearch {

    private final SearchQueryParser parser;
    private final SearchTokenRepository tokens;
    private final BlindIndexer blindIndexer;
    private final SearchProperties properties;

    DefaultPatientSearch(SearchTokenRepository tokens, BlindIndexer blindIndexer, SearchProperties properties) {
        this.parser = new SearchQueryParser(properties);
        this.tokens = tokens;
        this.blindIndexer = blindIndexer;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public SearchResult find(String query) {
        SearchQueryParser.Parsed parsed = parser.parse(query);
        return switch (parsed.type()) {
            case EMPTY -> SearchResult.empty();
            case INVALID -> SearchResult.rejected(parsed.messageKey(), parsed.messageArg());
            case AMKA -> single(parsed, TokenKind.AMKA);
            case EMAIL -> single(parsed, TokenKind.EMAIL);
            case MOBILE -> single(parsed, TokenKind.MOBILE);
            case NAME -> names(parsed);
        };
    }

    private SearchResult single(SearchQueryParser.Parsed parsed, TokenKind kind) {
        byte[] token = blindIndexer.token(kind.name(), parsed.values().get(0));
        return result(parsed.type(), tokens.findPatientIds(kind.name(), token, limit()));
    }

    private SearchResult names(SearchQueryParser.Parsed parsed) {
        List<byte[]> prefixTokens = parsed.values().stream()
                .map(prefix -> blindIndexer.token(TokenKind.NAME_PREFIX.name(), prefix)).toList();
        return result(QueryType.NAME,
                tokens.findPatientIdsHavingAll(TokenKind.NAME_PREFIX.name(), prefixTokens, prefixTokens.size(), limit()));
    }

    /** Asks for one more than the cap so truncation can be reported. */
    private PageRequest limit() {
        return PageRequest.of(0, properties.maxCandidates() + 1);
    }

    private SearchResult result(QueryType type, List<UUID> found) {
        boolean truncated = found.size() > properties.maxCandidates();
        Set<UUID> ids = new LinkedHashSet<>(truncated ? found.subList(0, properties.maxCandidates()) : found);
        return new SearchResult(type, ids, truncated, null, null);
    }
}
