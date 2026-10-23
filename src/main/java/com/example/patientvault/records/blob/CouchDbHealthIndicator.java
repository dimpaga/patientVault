package com.example.patientvault.records.blob;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness contributor "couchDb": initialised, reachable with the application account, no failure streak. No details. */
@Component("couchDbHealthIndicator")
class CouchDbHealthIndicator implements HealthIndicator {

    private final CouchDbInitializer initializer;
    private final CouchDbBlobStore store;

    CouchDbHealthIndicator(CouchDbInitializer initializer, CouchDbBlobStore store) {
        this.initializer = initializer;
        this.store = store;
    }

    @Override
    public Health health() {
        // probe first: a successful call is what clears the consecutive-failure counter
        boolean reachable = store.reachable();
        boolean up = initializer.isInitialized() && reachable && store.looksHealthy();
        return up ? Health.up().build() : Health.down().build();
    }
}
