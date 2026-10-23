package com.example.patientvault.crypto.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small LRU cache of unwrapped DEKs with a short TTL. Memory only, never serialised. */
final class DekCache {

    static final Duration TTL = Duration.ofMinutes(5);
    static final int MAX_ENTRIES = 1024;

    private record CachedKey(DataKeyImpl key, byte[] wrapped, Instant expiresAt) {
    }

    private final Clock clock;
    private final Map<UUID, CachedKey> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, CachedKey> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    DekCache(Clock clock) {
        this.clock = clock;
    }

    /** Returns the cached key only if it was unwrapped from exactly these wrapped bytes and has not expired. */
    synchronized DataKeyImpl get(UUID ownerId, byte[] wrapped) {
        CachedKey entry = entries.get(ownerId);
        if (entry == null) {
            return null;
        }
        if (clock.instant().isAfter(entry.expiresAt()) || !Arrays.equals(entry.wrapped(), wrapped)) {
            entries.remove(ownerId);
            return null;
        }
        return entry.key();
    }

    synchronized void put(UUID ownerId, byte[] wrapped, DataKeyImpl key) {
        entries.put(ownerId, new CachedKey(key, wrapped.clone(), clock.instant().plus(TTL)));
    }

    synchronized void evict(UUID ownerId) {
        entries.remove(ownerId);
    }
}
