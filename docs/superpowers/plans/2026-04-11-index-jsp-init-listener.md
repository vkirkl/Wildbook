# index.jsp — Remove Initialization Fallback from JSP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all first-boot initialization (default user, asset store, server info, profile keyword) out of `index.jsp` and into `StartupWildbook.contextInitialized()`, which already runs at container startup and is already registered in `web.xml`.

**Architecture:** `StartupWildbook` already implements `ServletContextListener` and is already registered in `web.xml`. The gap is that `contextInitialized()` never calls the four `ensure*()` methods that `initializeWildbook(request, myShepherd)` calls. The blocker is `ensureAssetStoreExists()`, which currently requires an `HttpServletRequest` to get the URL/scheme. The fix is to refactor it to use `SERVER_URL` env var (already used by `updateAssetStore()`) and `ServletContext.getRealPath("/")` instead of the request. Once that refactor is in place, all four `ensure*()` calls can move into `contextInitialized()`, and the `index.jsp` fallback becomes dead code and can be deleted.

**Tech Stack:** Java 17, Servlet API 4.0, JUnit 5, Mockito, Maven

---

## Root cause

`CommonConfiguration.isWildbookInitialized(shepherd)` returns `true` iff there is at least one user in the database. On a fresh install, no users exist, so it returns `false`.

`contextInitialized()` initializes plugins, queues, and caches but does **not** call:
- `ensureTomcatUserExists()` — creates the default `tomcat` admin user
- `ensureAssetStoreExists()` — creates the local asset store record
- `ensureServerInfo()` — sets server URL from `SERVER_URL` env var
- `ensureProfilePhotoKeywordExists()` — creates the ProfilePhoto keyword

So on a fresh install, the first request to `index.jsp` hits the fallback, creates the user, and returns. After that, `isWildbookInitialized()` returns `true` and the fallback is never hit again. This is a first-boot race condition: if two requests arrive simultaneously before the user is created, both find `isWildbookInitialized() == false` and both call `initializeWildbook()`.

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `src/main/java/org/ecocean/StartupWildbook.java` | Modify | Refactor `ensureAssetStoreExists()` to not need `HttpServletRequest`; add `ensure*()` calls to `contextInitialized()` |
| `src/main/webapp/index.jsp` | Modify | Remove the `isWildbookInitialized()` conditional block |
| `src/test/java/org/ecocean/StartupWildbookInitTest.java` | Create | Verify `ensureAssetStoreExists()` works without a request |

---

## Task 1: Refactor ensureAssetStoreExists() to work without HttpServletRequest

**Context:** `ensureAssetStoreExists(HttpServletRequest, Shepherd)` currently uses `request.getSession().getServletContext().getRealPath("/")` for the file path and `request.getScheme() + "://" + CommonConfiguration.getURLLocation(request)` for the URL. Both are available without a request: `getRealPath("/")` is on `ServletContext`, and the URL comes from `SERVER_URL` env var (which `ensureServerInfo()` and `updateAssetStore()` already use). If `SERVER_URL` is absent (local dev), fall back to a placeholder that `updateAssetStore()` can correct later.

**Files:**
- Modify: `src/main/java/org/ecocean/StartupWildbook.java`
- Create: `src/test/java/org/ecocean/StartupWildbookInitTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/StartupWildbookInitTest.java`:

```java
package org.ecocean;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartupWildbookInitTest {

    /**
     * Verifies that buildAssetStoreUrl() uses SERVER_URL when present,
     * not a request object.
     */
    @Test
    void buildAssetStoreUrl_withServerUrl_returnsDataUrl() {
        try (MockedStatic<System> sysStatic = mockStatic(System.class, CALLS_REAL_METHODS)) {
            sysStatic.when(() -> System.getenv("SERVER_URL"))
                     .thenReturn("https://mysite.example.com");

            String url = StartupWildbook.buildAssetStoreUrl();
            assertEquals("https://mysite.example.com/wildbook_data_dir", url);
        }
    }

    @Test
    void buildAssetStoreUrl_withoutServerUrl_returnsPlaceholder() {
        try (MockedStatic<System> sysStatic = mockStatic(System.class, CALLS_REAL_METHODS)) {
            sysStatic.when(() -> System.getenv("SERVER_URL")).thenReturn(null);

            String url = StartupWildbook.buildAssetStoreUrl();
            assertEquals("http://localhost/wildbook_data_dir", url);
        }
    }
}
```

- [ ] **Step 2: Run to confirm the test fails**

```bash
mvn test -pl . -Dtest="StartupWildbookInitTest" -q 2>&1 | tail -5
```

Expected: compilation error — `StartupWildbook.buildAssetStoreUrl()` does not exist yet.

- [ ] **Step 3: Add buildAssetStoreUrl() to StartupWildbook**

In `StartupWildbook.java`, add a new package-private static method (after `updateAssetStore()`):

```java
/**
 * Returns the data directory URL for asset store creation.
 * Uses the SERVER_URL env var when present (Docker deployments).
 * Falls back to a localhost placeholder when absent (dev mode — updateAssetStore()
 * will correct it when SERVER_URL becomes available at runtime).
 */
static String buildAssetStoreUrl() {
    String serverUrl = System.getenv("SERVER_URL");
    if (serverUrl != null && !serverUrl.isEmpty()) {
        return serverUrl + "/wildbook_data_dir";
    }
    return "http://localhost/wildbook_data_dir";
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
mvn test -pl . -Dtest="StartupWildbookInitTest" -q 2>&1 | tail -5
```

Expected:
```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 5: Add a new ensureAssetStoreExists(ServletContext, Shepherd) overload**

In `StartupWildbook.java`, add this new overload immediately after the existing `ensureAssetStoreExists(HttpServletRequest, Shepherd)`:

```java
/**
 * Context-listener-safe version of ensureAssetStoreExists.
 * Uses ServletContext for the file path and SERVER_URL for the web URL.
 * The original request-based overload is retained for backward compatibility
 * until all callers are migrated.
 */
public static void ensureAssetStoreExists(ServletContext sContext, Shepherd myShepherd) {
    String rootDir = sContext.getRealPath("/");
    String dataDir = ServletUtilities.dataDir("context0", rootDir);
    String dataUrl = buildAssetStoreUrl();

    myShepherd.beginDBTransaction();
    LocalAssetStore as = new LocalAssetStore("Default Local AssetStore",
        new File(dataDir).toPath(), dataUrl, true);
    myShepherd.getPM().makePersistent(as);
    myShepherd.commitDBTransaction();
}
```

- [ ] **Step 6: Verify it compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Checkpoint — pause for review**

---

## Task 2: Add ensure*() calls to contextInitialized()

**Files:**
- Modify: `src/main/java/org/ecocean/StartupWildbook.java`

Each `ensure*()` call needs its own `Shepherd` with a proper transaction, because they each commit independently.

- [ ] **Step 1: Add the initialization block to contextInitialized()**

In `contextInitialized()`, after the `if (skipInit(sce, null))` check and before `Setting.initialize(context)`, add:

```java
// First-boot initialization: create default user, asset store, server info, and keyword
// if they don't already exist. This replaces the fallback in index.jsp.
Shepherd initShepherd = new Shepherd(context);
initShepherd.setAction("StartupWildbook.contextInitialized.ensureInit");
try {
    ensureTomcatUserExists(initShepherd);
    ensureAssetStoreExists(sContext, initShepherd);
    ensureServerInfo(initShepherd);
    ensureProfilePhotoKeywordExists(initShepherd);
} catch (Exception e) {
    logger.error("First-boot initialization failed in contextInitialized()", e);
} finally {
    initShepherd.closeDBTransaction();
}
```

**Note:** `ensureTomcatUserExists()`, `ensureAssetStoreExists()`, and `ensureProfilePhotoKeywordExists()` each call `beginDBTransaction()` and `commitDBTransaction()` internally. `ensureServerInfo()` does not open a transaction (it calls `CommonConfiguration.setServerInfo()` which manages its own). The `finally` here closes the shepherd connection; it does not issue a redundant rollback since each inner method handles its own transaction lifecycle.

- [ ] **Step 2: Add the logger field to StartupWildbook**

At the top of the `StartupWildbook` class (after the class declaration), add:

```java
private static final Logger logger = LogManager.getLogger(StartupWildbook.class);
```

And add the import at the top of the file:

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
```

- [ ] **Step 3: Replace the System.out.println calls in StartupWildbook with logger calls**

While in this file, replace the remaining `System.out.println` calls (lines 66, 80, 95–96, 104, 109–110, 115, 133, 166–167, 169, 193) with appropriate `logger.info()`, `logger.warn()`, or `logger.error()` calls. Example:

```java
// line 66 area:
System.out.println("StartupWildbook.ensureServerInfo failed on " + urlString + ": " + mal.toString());
// becomes:
logger.error("ensureServerInfo: malformed SERVER_URL '{}': {}", urlString, mal.getMessage());
```

```java
// line 80 area:
System.out.println("StartupWildbook.ensureServerInfo updated server info to: " + info.toString());
// becomes:
logger.info("ensureServerInfo: updated server info to {}", info);
```

```java
// line 166–167 area in contextInitialized:
System.out.println(new org.joda.time.DateTime() + " ### StartupWildbook initialized for: " + servletContextInfo(sContext));
// becomes:
logger.info("StartupWildbook contextInitialized for: {}", servletContextInfo(sContext));
```

```java
// line 169:
System.out.println("- SKIPPED initialization due to skipInit()");
// becomes:
logger.info("contextInitialized: SKIPPED due to skipInit()");
```

```java
// line 193 area:
e.printStackTrace();
// becomes:
logger.error("Failed to start WildbookScheduledTaskThread", e);
```

Apply the same pattern to all remaining `System.out.println` and `e.printStackTrace()` calls in the file.

- [ ] **Step 4: Compile**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Checkpoint — pause for review**

---

## Task 3: Remove initialization fallback from index.jsp

**Files:**
- Modify: `src/main/webapp/index.jsp`

- [ ] **Step 1: Delete the isWildbookInitialized conditional block**

Find and delete the entire block (original lines 39–47):

```java
if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
  System.out.println("WARNING: index.jsp has determined that CommonConfiguration.isWildbookInitialized()==false!");
  %>
    <script type="text/javascript">
      console.log("Wildbook is not initialized!");
    </script>
  <%
  StartupWildbook.initializeWildbook(request, myShepherd);
}
```

(By this point Task 2 in Plan 1 will have already replaced the `System.out.println` on line 40 with a logger call — remove the whole block regardless.)

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
| Issue 2: App init inside JSP | All three tasks |
| Race condition on first boot | Task 2 (init runs at container startup, before any request) |
| `ensureAssetStoreExists` needs request | Task 1 (new overload using `ServletContext` + `buildAssetStoreUrl()`) |
| `System.out.println` in `StartupWildbook` | Task 2, Step 3 |

### Notes

- **Ordering relative to other plans:** This plan can be applied independently of Plans 1 and 2, but Plan 1 (Task 2, Step 3) also touches the `System.out.println` at original line 40. If both plans are applied, the line 40 System.out will be replaced by Plan 1 and then the whole block deleted by this plan — both operations are safe to do in either order.
- **Backward compatibility:** The original `ensureAssetStoreExists(HttpServletRequest, Shepherd)` overload is retained. `index.jsp` currently calls `initializeWildbook(request, myShepherd)` which calls the request-based overload. Once index.jsp stops calling it (Task 3 here), the request-based overload becomes unused and can be removed in a future cleanup. Do not remove it in this PR — it may be called from other JSPs.
- **Dev mode (no SERVER_URL):** On a local dev install without `SERVER_URL`, `buildAssetStoreUrl()` returns `http://localhost/wildbook_data_dir` as the placeholder. This is the same behavior as the current request-based path when running on localhost. `updateAssetStore()` will correct it once `SERVER_URL` is set.
- **skipInit():** `contextInitialized()` checks `skipInit()` before our new block. If `skipInit()` returns true (e.g., running tests), the `ensure*()` calls are correctly skipped.
- **Transaction management in ensure*():** `ensureTomcatUserExists()` opens its own transaction and commits if a user is created. If users already exist, it does nothing. `ensureAssetStoreExists()` always opens and commits a transaction (it persists a new `LocalAssetStore`). `ensureProfilePhotoKeywordExists()` calls `myShepherd.storeNewKeyword()` — verify this method commits internally; if not, a commit call may be needed after it.
