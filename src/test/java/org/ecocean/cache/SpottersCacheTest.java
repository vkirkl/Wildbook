package org.ecocean.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SpottersCacheTest {

    private SpottersCache cache;

    @BeforeEach
    void setUp() {
        // 1000ms TTL for fast-expiry tests
        cache = new SpottersCache(1000L);
    }

    @Test
    void get_whenEmpty_returnsNull() {
        assertNull(cache.get());
    }

    @Test
    void get_afterPut_returnsStoredMap() {
        Map<String, Integer> spotters = new LinkedHashMap<>();
        spotters.put("alice", 5);
        cache.put(spotters);

        Map<String, Integer> result = cache.get();
        assertNotNull(result);
        assertEquals(5, result.get("alice"));
    }

    @Test
    void get_afterTtlExpiry_returnsNull() throws InterruptedException {
        Map<String, Integer> spotters = new LinkedHashMap<>();
        spotters.put("alice", 5);
        cache.put(spotters);

        Thread.sleep(1100L); // wait past 1000ms TTL

        assertNull(cache.get());
    }

    @Test
    void get_beforeTtlExpiry_returnsValue() throws InterruptedException {
        Map<String, Integer> spotters = new LinkedHashMap<>();
        spotters.put("bob", 3);
        cache.put(spotters);

        Thread.sleep(500L); // within TTL

        assertNotNull(cache.get());
    }

    @Test
    void put_replacesExistingEntry() {
        Map<String, Integer> first = new LinkedHashMap<>();
        first.put("alice", 5);
        cache.put(first);

        Map<String, Integer> second = new LinkedHashMap<>();
        second.put("bob", 10);
        cache.put(second);

        Map<String, Integer> result = cache.get();
        assertNotNull(result);
        assertFalse(result.containsKey("alice"));
        assertTrue(result.containsKey("bob"));
    }

    @Test
    void isExpired_freshEntry_returnsFalse() {
        Map<String, Integer> spotters = new LinkedHashMap<>();
        spotters.put("alice", 1);
        cache.put(spotters);
        assertFalse(cache.isExpired());
    }

    @Test
    void isExpired_emptyCache_returnsTrue() {
        assertTrue(cache.isExpired());
    }
}
