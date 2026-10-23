package com.example.patientvault.search;

import java.util.Set;
import java.util.UUID;

/**
 * Candidate patients for a query, as ids only (the patient module decides visibility, ordering and what to
 * show). {@code messageKey} is set for rejected queries and explains what to change; {@code messageArg}
 * is an optional number for that message. {@code truncated} means more matches existed than were returned.
 */
public record SearchResult(QueryType type, Set<UUID> candidateIds, boolean truncated, String messageKey,
                           Object messageArg) {

    public static SearchResult empty() {
        return new SearchResult(QueryType.EMPTY, Set.of(), false, null, null);
    }

    public static SearchResult rejected(String messageKey, Object arg) {
        return new SearchResult(QueryType.INVALID, Set.of(), false, messageKey, arg);
    }

    @Override
    public String toString() {
        return "SearchResult[type=" + type + ", candidates=" + candidateIds.size() + "]";
    }
}
