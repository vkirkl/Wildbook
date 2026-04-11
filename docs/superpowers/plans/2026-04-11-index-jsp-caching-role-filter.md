# index.jsp Caching and Role-Based Spotter Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache the top-spotters query with a 30-minute TTL, and replace the hardcoded system-account username blacklist with a role-based check using the existing `User.hasRoleByName()` API.

**Architecture:** The spotters cache is a new `SpottersCache` helper class (a simple in-memory map with a timestamp) so it can be unit-tested without a servlet container. The role-based filter uses the existing `User.hasRoleByName("admin", shepherd)` method. System service accounts (`siowamteam`, `tomcat`) that are not admins should be granted an `admin` role or a new `system` role in Wildbook's admin UI — that is a deployment step, not a code step.

**Prerequisite:** `2026-04-11-index-jsp-cleanup-security-correctness.md` should be applied first, since it fixes the double `getUser()` call (C2) which this plan builds on.

**Tech Stack:** Java 17, JSP 2.3, Log4j2, JUnit 5, Maven

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `src/main/java/org/ecocean/cache/SpottersCache.java` | Create | Thread-safe in-memory cache for the spotters map with configurable TTL |
| `src/test/java/org/ecocean/cache/SpottersCacheTest.java` | Create | Unit tests for TTL expiry and thread safety |
| `src/main/webapp/index.jsp` | Modify | Use `SpottersCache` and replace hardcoded blacklist with role check |

---

## Task 1: Create SpottersCache with TTL (D1)

`QueryCache` is designed for count queries backed by `StoredQuery` DB records. The spotters query returns a `Map<String, Integer>` — a different shape. We need a lightweight, independent cache.

**Files:**
- Create: `src/main/java/org/ecocean/cache/SpottersCache.java`
- Create: `src/test/java/org/ecocean/cache/SpottersCacheTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/cache/SpottersCacheTest.java`:

```java
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
```

- [ ] **Step 2: Run to confirm tests fail**

```bash
mvn test -pl . -Dtest="SpottersCacheTest" -q 2>&1 | tail -5
```

Expected: compilation error — `SpottersCache` does not exist yet.

- [ ] **Step 3: Create `SpottersCache`**

Create `src/main/java/org/ecocean/cache/SpottersCache.java`:

```java
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
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn test -pl . -Dtest="SpottersCacheTest" -q 2>&1 | tail -5
```

Expected:
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 5: Checkpoint — pause for review**

---

## Task 2: Wire SpottersCache into index.jsp (D1 continued)

The cache needs to survive across requests. The right place is `ServletContext` — one cache object per application instance.

**Files:**
- Modify: `src/main/webapp/index.jsp`

- [ ] **Step 1: Add the SpottersCache import**

In the page imports at the top of `index.jsp`, add:
```jsp
<%@ page import="org.ecocean.cache.SpottersCache" %>
```

- [ ] **Step 2: Initialize or retrieve the cache from ServletContext**

At the start of the spotters scriptlet block (just before the `startTime` line), add:

```java
SpottersCache spottersCache = (SpottersCache) application.getAttribute("spottersCache");
if (spottersCache == null) {
    synchronized (application) {
        spottersCache = (SpottersCache) application.getAttribute("spottersCache");
        if (spottersCache == null) {
            spottersCache = new SpottersCache();
            application.setAttribute("spottersCache", spottersCache);
        }
    }
}
```

Note: `application` is the implicit JSP object for `ServletContext`.

- [ ] **Step 3: Use the cache for the spotters query**

Find the current spotters fetch:
```java
Map<String,Integer> spotters = myShepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(startTime);
```
Replace with:
```java
Map<String,Integer> spotters = spottersCache.get();
if (spotters == null) {
    spotters = myShepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(startTime);
    if (spotters != null) {
        spottersCache.put(spotters);
    }
}
if (spotters == null) {
    spotters = new java.util.LinkedHashMap<>();
}
```

- [ ] **Step 4: Verify the file compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Checkpoint — pause for review**

---

## Task 3: Replace hardcoded username blacklist with role-based check (D2)

**Files:**
- Modify: `src/main/webapp/index.jsp`

The existing API: `User.hasRoleByName(String roleName, Shepherd shepherd)` returns true if the user has that role. `User.isAdmin(Shepherd shepherd)` delegates to `hasRoleByName("admin", shepherd)`.

**Deployment prerequisite (not a code step):** System accounts `siowamteam` and `tomcat` must be granted the `admin` role (or a new `system` role if one is created) in Wildbook's admin UI before this code change goes live. Without this, those accounts will appear in the top-spotters list. Coordinate with ops.

- [ ] **Step 1: Replace the blacklist condition in the spotters loop**

After the Plan 1 fix (C2), the spotters loop looks like:
```java
User thisUser=myShepherd.getUser(spotter);
if(!spotter.equals("siowamteam") && !spotter.equals("admin") && !spotter.equals("tomcat") && thisUser!=null){
```

Replace with:
```java
User thisUser = myShepherd.getUser(spotter);
if (thisUser != null && !thisUser.isAdmin(myShepherd)) {
```

- [ ] **Step 2: Verify the file compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Run the full test suite**

```bash
mvn test -pl . -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` with no new failures.

- [ ] **Step 4: Checkpoint — pause for review**

---

## Self-Review Checklist

### Spec coverage

| Issue | Covered by |
|-------|-----------|
| D1: Cache top spotters query with TTL | Tasks 1 and 2 |
| D2: Replace hardcoded username blacklist | Task 3 |

### Notes

- **Cache scope:** `SpottersCache` is stored in `ServletContext` (the `application` implicit object in JSP). This means one cache per JVM/app — correct for a single-node deployment. Multi-node deployments would need a shared cache (Redis etc.), but that is out of scope here.
- **Double-checked locking:** The `synchronized(application)` block uses double-checked locking to avoid creating two cache instances on first request under concurrent load. This is safe because `AtomicReference` ensures visibility.
- **isAdmin() and system accounts:** `isAdmin()` checks for the `"admin"` role only. If `siowamteam` or `tomcat` are not granted admin role, they will appear in the spotters list. The deployment prerequisite must be completed before this PR goes live.
- **Null spotters guard:** The `if (spotters == null)` fallback to an empty `LinkedHashMap` prevents a `NullPointerException` if the query fails while the cache is empty.
