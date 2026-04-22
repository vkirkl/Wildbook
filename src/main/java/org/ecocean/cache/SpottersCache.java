package org.ecocean.cache;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory cache for the top-spotters map returned by
 * Shepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder().
 *
 * A single shared instance should be stored in ServletContext attributes
 * and retrieved per-request. The cache refreshes automatically when the TTL expires.
 */
public class SpottersCache {

    /** Default TTL: 30 minutes */
    public static final long DEFAULT_TTL_MS = 30L * 60L * 1000L;

    private final long ttlMs;
    private final AtomicReference<Map<String, Integer>> cachedMap = new AtomicReference<>(null);
    private final AtomicLong expiresAt = new AtomicLong(0L);

    public SpottersCache() {
        this(DEFAULT_TTL_MS);
    }

    public SpottersCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    /**
     * Returns the cached map if present and not expired, otherwise null.
     * Callers should check for null and re-fetch from the database.
     */
    public Map<String, Integer> get() {
        if (isExpired()) return null;
        return cachedMap.get();
    }

    /**
     * Stores a new map and resets the TTL clock.
     */
    public void put(Map<String, Integer> spotters) {
        cachedMap.set(spotters);
        expiresAt.set(System.currentTimeMillis() + ttlMs);
    }

    /**
     * Returns true if the cache is empty or the TTL has elapsed.
     */
    public boolean isExpired() {
        return cachedMap.get() == null || System.currentTimeMillis() > expiresAt.get();
    }
}
