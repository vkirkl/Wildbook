# index.jsp Cleanup, Security, and Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove logging noise, dead code, and security vulnerabilities (XSS, path traversal) from `index.jsp`, and fix two correctness bugs (uncached encounter count, double `getUser()` call) and broken transaction management.

**Architecture:** All changes are in `index.jsp` except path-traversal validation, which is extracted into a new `IndexPageHelper` class so it can be unit-tested independently of the JSP. All changes are backward-compatible — no database schema or API changes.

**Tech Stack:** Java 17, JSP 2.3, JSTL 1.2, Log4j2 2.24.1, JUnit 5, Mockito, Maven

**Source file:** `src/main/webapp/index.jsp`

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `src/main/webapp/index.jsp` | Modify | All logging, dead code, XSS, transaction, and correctness fixes |
| `src/main/java/org/ecocean/servlet/IndexPageHelper.java` | Create | `buildSafeProfilePhotoUrl()` — testable path-traversal guard |
| `src/test/java/org/ecocean/servlet/IndexPageHelperTest.java` | Create | Unit tests for the helper |

---

## Task 1: Create IndexPageHelper with safe URL builder (B2)

This is the only logic in this plan that can be unit-tested, so we write it first (TDD).

**Files:**
- Create: `src/main/java/org/ecocean/servlet/IndexPageHelper.java`
- Create: `src/test/java/org/ecocean/servlet/IndexPageHelperTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/servlet/IndexPageHelperTest.java`:

```java
package org.ecocean.servlet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexPageHelperTest {

    @Test
    void buildSafeProfilePhotoUrl_normalInputs_buildsCorrectUrl() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", "photo.jpg");
        assertEquals("/wildbook_data_dir/users/alice/photo.jpg", url);
    }

    @Test
    void buildSafeProfilePhotoUrl_usernameWithSlash_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "../admin", "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_filenameWithDotDot_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", "../../etc/passwd");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_filenameWithBackslash_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", "evil\\file.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_nullUsername_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", null, "photo.jpg");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_nullFilename_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", null);
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }

    @Test
    void buildSafeProfilePhotoUrl_emptyFilename_returnsDefault() {
        String url = IndexPageHelper.buildSafeProfilePhotoUrl(
            "wildbook_data_dir", "alice", "");
        assertEquals(IndexPageHelper.DEFAULT_PHOTO_URL, url);
    }
}
```

- [ ] **Step 2: Run to confirm tests fail**

```bash
mvn test -pl . -Dtest="IndexPageHelperTest" -q 2>&1 | tail -5
```

Expected: compilation error — `IndexPageHelper` does not exist yet.

- [ ] **Step 3: Create `IndexPageHelper`**

Create `src/main/java/org/ecocean/servlet/IndexPageHelper.java`:

```java
package org.ecocean.servlet;

/**
 * Stateless utility methods for index.jsp rendering logic.
 * Extracted here so they can be unit-tested without a running servlet container.
 */
public class IndexPageHelper {

    public static final String DEFAULT_PHOTO_URL =
        "images/user-profile-white-transparent.png";

    private IndexPageHelper() {}

    /**
     * Constructs a profile photo URL from its components, returning the default
     * placeholder if any component is null, empty, or contains path-separator
     * characters that could enable directory traversal.
     *
     * @param dataDirectoryName  value from CommonConfiguration.getDataDirectoryName()
     * @param username           the user's login name
     * @param filename           the photo filename from UserImage
     * @return safe URL string
     */
    public static String buildSafeProfilePhotoUrl(
            String dataDirectoryName, String username, String filename) {
        if (dataDirectoryName == null || username == null || filename == null
                || username.isEmpty() || filename.isEmpty()) {
            return DEFAULT_PHOTO_URL;
        }
        if (containsPathSeparator(username) || containsPathSeparator(filename)) {
            return DEFAULT_PHOTO_URL;
        }
        return "/" + dataDirectoryName + "/users/" + username + "/" + filename;
    }

    private static boolean containsPathSeparator(String s) {
        return s.contains("/") || s.contains("\\") || s.contains("..");
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn test -pl . -Dtest="IndexPageHelperTest" -q 2>&1 | tail -5
```

Expected:
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 5: Checkpoint — pause for review**

---

## Task 2: Add logger and replace all System.out / printStackTrace (A1)

JSP scriptlets cannot have `static final` fields. Log4j2 caches loggers by name, so calling `LogManager.getLogger("index.jsp")` on every request returns the same cached `Logger` instance — no performance concern.

**Files:**
- Modify: `src/main/webapp/index.jsp`

- [ ] **Step 1: Add import directives at the top of index.jsp**

After the existing `<%@ page import="..." %>` directives (after line 14), add:

```jsp
<%@ page import="org.apache.logging.log4j.LogManager" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
```

- [ ] **Step 2: Declare the logger at the top of the first scriptlet block**

At the very start of the `<%` scriptlet that begins around line 20 (just after `String context=ServletUtilities.getContext(request);`), add as the first statement:

```java
final Logger logger = LogManager.getLogger("index.jsp");
```

- [ ] **Step 3: Replace the initialization warning (original line 40)**

Find:
```java
System.out.println("WARNING: index.jsp has determined that CommonConfiguration.isWildbookInitialized()==false!");
```
Replace with:
```java
logger.warn("Wildbook not initialized at page render time; calling initializeWildbook");
```

- [ ] **Step 4: Replace the QueryCache exception handler (original lines 84–88)**

Find the catch block:
```java
catch(Exception e){
    System.out.println("INFO: *** If you are seeing an exception here (via index.jsp) your likely need to setup QueryCache");
    System.out.println("      *** This entails configuring a directory via cache.properties and running appadmin/testQueryCache.jsp");
    e.printStackTrace();
}
```
Replace with:
```java
catch(Exception e){
    logger.warn("QueryCache not configured — check cache.properties and run appadmin/testQueryCache.jsp");
    logger.error("QueryCache query failed", e);
}
```

- [ ] **Step 5: Replace printStackTrace in the featured user catch block (original line 304)**

Find:
```java
catch(Exception e){e.printStackTrace();}
```
immediately after the `if(featuredUser!=null){...}` block. Replace with:
```java
catch(Exception e){ logger.error("Failed to render featured user section", e); }
```

- [ ] **Step 6: Replace printStackTrace in the encounters catch block (original line 345)**

Find:
```java
catch(Exception e){e.printStackTrace();}
```
inside the latest encounters try block. Replace with:
```java
catch(Exception e){ logger.error("Failed to render recent encounters section", e); }
```

- [ ] **Step 7: Delete the startTime debug println (original line 367)**

Find and delete this line entirely:
```java
System.out.println("  I think my startTime is: "+startTime);
```

- [ ] **Step 8: Replace printStackTrace in the spotters catch block (original line 405)**

Find:
```java
catch(Exception e){e.printStackTrace();}
```
in the top spotters try block. Replace with:
```java
catch(Exception e){ logger.error("Failed to render top spotters section", e); }
```

- [ ] **Step 9: Verify no System.out or printStackTrace remain**

```bash
grep -n "System\.out\|printStackTrace" src/main/webapp/index.jsp
```

Expected: no output.

- [ ] **Step 10: Verify the file compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 11: Checkpoint — pause for review**

---

## Task 3: Delete dead and commented-out code, extract 30-day constant (A2, A3)

**Files:**
- Modify: `src/main/webapp/index.jsp`

- [ ] **Step 1: Delete the commented-out redirect block (original lines 64–67)**

Find and delete these four lines (they appear right after `QueryCache qc=QueryCacheFactory.getQueryCache(context);`):
```java
//String url = "/react/login";
//response.sendRedirect(url);
//RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(url);
//dispatcher.forward(request, response);
```

- [ ] **Step 2: Delete the commented beginDBTransaction in the featured user section (original line 271)**

Find and delete:
```java
//myShepherd.beginDBTransaction();
```
which appears just before `try{` in the featured user section.

- [ ] **Step 3: Delete the commented rollbackDBTransaction in the featured user finally (original line 307)**

Find and delete:
```java
//myShepherd.rollbackDBTransaction();
```
inside the `finally{` block of the featured user section.

- [ ] **Step 4: Delete commented-out lines in the top spotters section (original lines 362, 364, 383–385, 406)**

Find and delete:
```java
//myShepherd.beginDBTransaction();
```
```java
//System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
```
```java
//System.out.println(spotters.values().toString());
```
```java
//System.out.println(spotters);
```
```java
//finally{myShepherd.rollbackDBTransaction();}
```

- [ ] **Step 5: Replace the inline 30-day arithmetic with a named constant (A3)**

In the spotters scriptlet, find:
```java
long startTime = System.currentTimeMillis() - Long.valueOf(1000L*60L*60L*24L*30L);
```
Replace with:
```java
final long THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L;
long startTime = System.currentTimeMillis() - THIRTY_DAYS_MS;
```

- [ ] **Step 6: Verify the file compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Checkpoint — pause for review**

---

## Task 4: Fix path-traversal in profile photo URLs using IndexPageHelper (B2)

**Files:**
- Modify: `src/main/webapp/index.jsp`

The class was created in Task 1. Now use it in two places in the JSP.

- [ ] **Step 1: Add the IndexPageHelper import to index.jsp**

In the page imports at the top of the file, add:
```jsp
<%@ page import="org.ecocean.servlet.IndexPageHelper" %>
```

- [ ] **Step 2: Replace the featured user photo URL construction (original line 277)**

Find:
```java
String profilePhotoURL="images/user-profile-white-transparent.png";
if(featuredUser.getUserImage()!=null){
    profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+featuredUser.getUsername()+"/"+featuredUser.getUserImage().getFilename();
}
```
Replace with:
```java
String profilePhotoURL = IndexPageHelper.DEFAULT_PHOTO_URL;
if (featuredUser.getUserImage() != null) {
    profilePhotoURL = IndexPageHelper.buildSafeProfilePhotoUrl(
        CommonConfiguration.getDataDirectoryName(context),
        featuredUser.getUsername(),
        featuredUser.getUserImage().getFilename());
}
```

- [ ] **Step 3: Replace the spotters photo URL construction (original line 379–382)**

Find:
```java
String profilePhotoURL="images/user-profile-white-transparent.png";
User thisUser=myShepherd.getUser(spotter);
if(thisUser.getUserImage()!=null){
    profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
}
```
Replace with:
```java
String profilePhotoURL = IndexPageHelper.DEFAULT_PHOTO_URL;
if (thisUser.getUserImage() != null) {
    profilePhotoURL = IndexPageHelper.buildSafeProfilePhotoUrl(
        CommonConfiguration.getDataDirectoryName(context),
        thisUser.getUsername(),
        thisUser.getUserImage().getFilename());
}
```

- [ ] **Step 4: Verify the file compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Checkpoint — pause for review**

---

## Task 5: Escape unsafe HTML output to fix XSS (B1)

**Files:**
- Modify: `src/main/webapp/index.jsp`

JSTL 1.2 is already on the classpath (`jstl:jstl:1.2` in pom.xml). `<c:out>` escapes HTML special characters by default. It accepts JSP runtime expressions (`<%= %>`) in its `value` attribute.

For the encounter `href`, URL-encoding is the right fix — `<c:out>` is for HTML content, not URL parameters.

- [ ] **Step 1: Add the JSTL core taglib directive and URLEncoder import**

At the top of `index.jsp`, add the taglib after the existing page directives:
```jsp
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
```

Add to the page imports:
```jsp
<%@ page import="java.net.URLEncoder" %>
```

- [ ] **Step 2: Escape featured user fullName (original line 286)**

Find:
```jsp
<p><%=featuredUser.getFullName() %>
```
Replace with:
```jsp
<p><c:out value="<%= featuredUser.getFullName() %>"/>
```

- [ ] **Step 3: Escape featured user affiliation (original line 290)**

Find:
```jsp
<i><%=featuredUser.getAffiliation() %></i>
```
Replace with:
```jsp
<i><c:out value="<%= featuredUser.getAffiliation() %>"/></i>
```

- [ ] **Step 4: Escape featured user statement (original line 295)**

Find:
```jsp
<p><%=featuredUser.getUserStatement() %></p>
```
Replace with:
```jsp
<p><c:out value="<%= featuredUser.getUserStatement() %>"/></p>
```

- [ ] **Step 5: URL-encode the encounter catalog number in the href, and escape the display name (original line 338)**

Find:
```jsp
<p><a href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" title=""><%=thisEnc.getDisplayName() %></a></p>
```
Replace with:
```jsp
<p><a href="encounters/encounter.jsp?number=<%= URLEncoder.encode(thisEnc.getCatalogNumber() != null ? thisEnc.getCatalogNumber() : "", "UTF-8") %>" title=""><c:out value="<%= thisEnc.getDisplayName() %>"/></a></p>
```

- [ ] **Step 6: Escape spotter affiliation (original line 393)**

Find:
```jsp
<small><%=thisUser.getAffiliation() %></small>
```
Replace with:
```jsp
<small><c:out value="<%= thisUser.getAffiliation() %>"/></small>
```

- [ ] **Step 7: Escape spotter username in display (original line 397)**

Find:
```jsp
<p><a href="#" title=""><%=spotter %>, <span><%=numUserEncs %> <%=props.getProperty("encounters") %><span></p>
```
Replace with:
```jsp
<p><a href="#" title=""><c:out value="<%= spotter %>"/>, <span><%=numUserEncs %> <%=props.getProperty("encounters") %><span></p>
```

- [ ] **Step 8: Verify the file compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 9: Checkpoint — pause for review**

---

## Task 6: Restore numEncounters to QueryCache and fix double getUser() (C1, C2)

**Files:**
- Modify: `src/main/webapp/index.jsp`

- [ ] **Step 1: Restore numEncounters to QueryCache (C1)**

In the stats block, find:
```java
numEncounters=myShepherd.getNumEncounters();
//numEncounters=qc.getQueryByName("numEncounters").executeCountQuery(myShepherd).intValue();
```
Replace both lines with:
```java
numEncounters=qc.getQueryByName("numEncounters").executeCountQuery(myShepherd).intValue();
```

- [ ] **Step 2: Fix double getUser() in the spotters loop (C2)**

In the top spotters while loop, find:
```java
if(!spotter.equals("siowamteam") && !spotter.equals("admin") && !spotter.equals("tomcat") && myShepherd.getUser(spotter)!=null){
    String profilePhotoURL = IndexPageHelper.DEFAULT_PHOTO_URL;
    User thisUser=myShepherd.getUser(spotter);
```
Replace with (fetch once, use for both the null check and as `thisUser`):
```java
User thisUser=myShepherd.getUser(spotter);
if(!spotter.equals("siowamteam") && !spotter.equals("admin") && !spotter.equals("tomcat") && thisUser!=null){
    String profilePhotoURL = IndexPageHelper.DEFAULT_PHOTO_URL;
```

- [ ] **Step 3: Verify the file compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Checkpoint — pause for review**

---

## Task 7: Fix transaction management (C3)

**Files:**
- Modify: `src/main/webapp/index.jsp`

**Context:** The page currently has two `beginDBTransaction()` calls — one at the top (line 61) and a nested one before the encounters loop (line 320). The nested one is wrong: it opens a second transaction on top of the first. The corresponding `rollbackDBTransaction()` in the encounters `finally` (line 347) then closes the *outer* transaction, leaving the spotters section running with no active transaction.

The correct structure: one transaction opened at the top, covering all queries, closed in the final `rollbackDBTransaction()` + `closeDBTransaction()` at the bottom of the file.

- [ ] **Step 1: Remove the nested beginDBTransaction in the encounters section**

Find this line immediately before the encounters `try{` block:
```java
myShepherd.beginDBTransaction();
```
Delete it. (The outer transaction from the top of the file covers this section.)

- [ ] **Step 2: Remove the rollbackDBTransaction from the encounters finally**

Find the encounters section `finally` block:
```java
finally{
    myShepherd.rollbackDBTransaction();
}
```
Remove the `rollbackDBTransaction()` call. Leave the `finally{}` block in place if other cleanup is needed, or remove the empty `finally{}` entirely:
```java
// finally block removed — outer transaction handles cleanup
```

- [ ] **Step 3: Verify the spotters section finally block is correct**

After Task 3's dead-code cleanup, the spotters try/catch/finally should look like:
```java
try{
    // ...spotters rendering...
}
catch(Exception e){ logger.error("Failed to render top spotters section", e); }
```
There should be no dangling finally block here. Confirm this is the case and the block ends cleanly.

- [ ] **Step 4: Verify the outer transaction cleanup at the end of the file**

Confirm these lines remain at the very bottom of the file, outside all try/catch blocks:
```java
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
```

- [ ] **Step 5: Verify the file compiles**

```bash
mvn compile -pl . -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Run the full test suite to catch regressions**

```bash
mvn test -pl . -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` with no new failures.

- [ ] **Step 7: Checkpoint — pause for review**

---

## Self-Review Checklist

### Spec coverage

| Issue | Covered by |
|-------|-----------|
| A1: Debug println | Task 2, Step 7 |
| A2: Dead/commented code | Task 3, Steps 1–4 |
| A3: Magic number constant | Task 3, Step 5 |
| B1: XSS via unescaped output | Task 5 |
| B2: Path traversal | Tasks 1 and 4 |
| C1: numEncounters not using QueryCache | Task 6, Step 1 |
| C2: Double getUser() | Task 6, Step 2 |
| C3: Broken transaction management | Task 7 |
| All System.out / printStackTrace | Task 2 |

### Notes

- **Transaction cleanup (C3):** Removing the nested `beginDBTransaction` and the mid-page `rollbackDBTransaction` are the key changes. The encounters and spotters sections both become part of the single outer transaction. Verify in logs after deploy that no `transaction not active` or `no active transaction` errors appear.
- **JSTL c:out with scriptlet expressions:** `<c:out value="<%= expr %>" />` is valid JSTL 1.2 — the `value` attribute accepts rtexprvalue. If the compiler rejects this syntax, the fallback is `<% pageContext.setAttribute("v", expr); %>${fn:escapeXml(v)}` with a fn taglib.
- **numEncounters QueryCache:** The `numEncounters` named query must exist in the QueryCache config (it was previously working — the direct DB call was a regression). If it throws on `getQueryByName("numEncounters")`, restore the direct call temporarily and file a follow-up to add the missing cache entry.
