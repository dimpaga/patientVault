package com.example.patientvault.search.internal;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/** One blind-index token. Always inserted fresh, so {@link Persistable} avoids a SELECT per token. */
@Entity
@Table(name = "patient_search_token")
public class SearchToken implements Persistable<SearchTokenId> {

    @EmbeddedId
    private SearchTokenId id;

    @Transient
    private boolean isNew = true;

    protected SearchToken() {
        this.isNew = false;
    }

    public SearchToken(SearchTokenId id) {
        this.id = id;
    }

    @Override
    public SearchTokenId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public String toString() {
        return "SearchToken[" + id + "]";
    }
}
