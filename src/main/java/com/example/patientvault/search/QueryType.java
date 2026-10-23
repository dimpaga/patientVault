package com.example.patientvault.search;

/** What a search query was recognised as (spec 6.3). */
public enum QueryType {
    /** Blank query: callers show the most recent patients. */
    EMPTY,
    /** 11 digits. */
    AMKA,
    /** Contains an at-sign. */
    EMAIL,
    /** Mostly digits with at least 6 of them. */
    MOBILE,
    /** One or more name words (prefix search, all words must match). */
    NAME,
    /** Rejected with a helpful message instead of an error. */
    INVALID
}
