// src/test/java/org/ecocean/LocationIDTest.java
package org.ecocean;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;

class LocationIDTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearCache() {
        LocationID.getJSONMaps().clear();
    }

    @Test
    void loadJSONData_loadsFromEnvVarPath() throws Exception {
        Path bundlesDir = tempDir.resolve("WEB-INF/classes/bundles");
        Files.createDirectories(bundlesDir);
        Files.writeString(bundlesDir.resolve("locationID.json"),
            "{\"id\": \"test-location\", \"name\": \"Test\"}");

        try (MockedStatic<CommonConfiguration> mockCfg =
                 mockStatic(CommonConfiguration.class, CALLS_REAL_METHODS)) {
            mockCfg.when(CommonConfiguration::getWildbookDataDir)
                   .thenReturn(tempDir.toString());

            JSONObject result = LocationID.getLocationIDStructure();

            assertNotNull(result);
            assertEquals("test-location", result.getString("id"));
        }
    }

    @Test
    void loadJSONData_fallsBackToClasspathWhenFileAbsent() {
        try (MockedStatic<CommonConfiguration> mockCfg =
                 mockStatic(CommonConfiguration.class, CALLS_REAL_METHODS)) {
            mockCfg.when(CommonConfiguration::getWildbookDataDir)
                   .thenReturn("/nonexistent/path/that/does/not/exist");

            JSONObject result = LocationID.getLocationIDStructure();
            assertNotNull(result, "Classpath fallback should return a non-null JSONObject");
        }
    }

    @Test
    void loadJSONData_doesNotUseShepherdDataDirForPath() throws Exception {
        Path bundlesDir = tempDir.resolve("WEB-INF/classes/bundles");
        Files.createDirectories(bundlesDir);
        Files.writeString(bundlesDir.resolve("locationID.json"),
            "{\"id\": \"env-var-location\"}");

        try (MockedStatic<CommonConfiguration> mockCfg =
                 mockStatic(CommonConfiguration.class, CALLS_REAL_METHODS)) {
            mockCfg.when(CommonConfiguration::getWildbookDataDir)
                   .thenReturn(tempDir.toString());
            // Stub getShepherdDataDir to confirm it is NOT consulted by loadJSONData after the fix.
            // If the old CWD-relative path logic were re-introduced, this stub would cause it
            // to look in a non-existent directory, failing the assertion below.
            mockCfg.when(() -> CommonConfiguration.getShepherdDataDir(anyString()))
                   .thenReturn("some_other_data_dir");

            JSONObject result = LocationID.getLocationIDStructure();

            assertNotNull(result);
            assertEquals("env-var-location", result.getString("id"),
                "Should load from WILDBOOK_DATA_DIR, not from shepherdDataDir path");
        }
    }

    @Test
    void loadJSONData_fallsBackToDefaultWhenQualifiedResourceAbsent() {
        // No override file, no locationID_unknown.json on classpath —
        // should fall back to locationID.json from classpath (Level 3).
        try (MockedStatic<CommonConfiguration> mockCfg =
                 mockStatic(CommonConfiguration.class, CALLS_REAL_METHODS)) {
            mockCfg.when(CommonConfiguration::getWildbookDataDir)
                   .thenReturn("/nonexistent/path/that/does/not/exist");

            // A qualifier with no matching classpath resource triggers Level-3 fallback
            JSONObject result = LocationID.getLocationIDStructure("unknown_qualifier_xyz");
            assertNotNull(result, "Level-3 fallback to default locationID.json should return non-null");
        }
    }
}
