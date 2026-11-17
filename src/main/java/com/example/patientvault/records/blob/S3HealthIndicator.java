package com.example.patientvault.records.blob;

import java.time.Duration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness contributor "s3": the bucket is reachable with the application's credentials. Cached for 60 s, no details. */
@Component("s3HealthIndicator")
class S3HealthIndicator implements HealthIndicator {

    private static final long TTL_NANOS = Duration.ofSeconds(60).toNanos();

    private final S3BlobStore store;
    private long checkedAt;
    private boolean up;
    private boolean checked;

    S3HealthIndicator(S3BlobStore store) {
        this.store = store;
    }

    @Override
    public synchronized Health health() {
        long now = System.nanoTime();
        if (!checked || now - checkedAt > TTL_NANOS) {
            up = store.reachable();
            checkedAt = now;
            checked = true;
        }
        return up ? Health.up().build() : Health.down().build();
    }
}
