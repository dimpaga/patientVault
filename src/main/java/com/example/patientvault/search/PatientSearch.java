package com.example.patientvault.search;

/**
 * Single search box lookup. The query is classified (AMKA, e-mail, mobile or name words) and matched
 * against blind-index tokens only; the query text is never stored or logged here.
 */
public interface PatientSearch {

    SearchResult find(String query);
}
